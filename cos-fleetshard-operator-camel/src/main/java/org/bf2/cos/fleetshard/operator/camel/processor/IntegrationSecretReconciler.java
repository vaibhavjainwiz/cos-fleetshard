package org.bf2.cos.fleetshard.operator.camel.processor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleet.manager.model.ServiceAccount;
import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ServiceAccountSpec;
import org.bf2.cos.fleetshard.api.ServiceAccountSpecBuilder;
import org.bf2.cos.fleetshard.operator.camel.CamelOperandConfiguration;
import org.bf2.cos.fleetshard.operator.support.Comparator;
import org.bf2.cos.fleetshard.operator.support.DeltaProcessor;
import org.bf2.cos.fleetshard.operator.support.Reconciler;
import org.bf2.cos.fleetshard.support.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;

import static org.bf2.cos.fleetshard.operator.camel.CamelConstants.APPLICATION_PROPERTIES;
import static org.bf2.cos.fleetshard.support.CollectionUtils.asBytesBase64;
import static org.bf2.cos.fleetshard.support.resources.Secrets.SECRET_ENTRY_SERVICE_ACCOUNT;
import static org.bf2.cos.fleetshard.support.resources.Secrets.extract;

@ApplicationScoped
public class IntegrationSecretReconciler implements Reconciler<ManagedProcessor, Secret> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationSecretReconciler.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    CamelOperandConfiguration configuration;

    @Inject
    ProcessorResourceEnricher processorResourceEnricher;

    @Inject
    DeltaProcessor deltaProcessor;

    public boolean reconcile(ManagedProcessor processor) {
        var requestedResource = createRequestedSecret(processor);
        var deployedResource = fetchDeployedSecret(processor);
        return deltaProcessor.processDelta(getComparator(), requestedResource, deployedResource);
    }

    @Override
    public Comparator<Secret> getComparator() {
        return new SecretComparator();
    }

    private Secret createRequestedSecret(ManagedProcessor processor) {
        final Map<String, String> properties = createSecretsData(processor);

        final Secret secret = new Secret();
        secret.setMetadata(new ObjectMeta());
        secret.getMetadata().setName(getIntegrationSecretName(processor));
        secret.getMetadata().setNamespace(processor.getMetadata().getNamespace());
        secret.setData(Map.of(APPLICATION_PROPERTIES, asBytesBase64(properties)));

        processorResourceEnricher.appendLabels(processor, secret);
        processorResourceEnricher.appendAnnotations(processor, secret);
        processorResourceEnricher.appendOwnerReference(processor, secret);
        return secret;
    }

    private Secret fetchDeployedSecret(ManagedProcessor processor) {
        return kubernetesClient.resources(Secret.class)
            .inNamespace(processor.getMetadata().getNamespace())
            .withName(getIntegrationSecretName(processor)).get();
    }

    private Map<String, String> createSecretsData(ManagedProcessor processor) {
        final Map<String, String> props = new TreeMap<>();

        var serviceAccountSecret = fetchServiceAccountSecret(processor);
        ServiceAccountSpec serviceAccountSpec = extractServiceAccountSpec(serviceAccountSecret);
        props.put("camel.component.kafka.brokers", processor.getSpec().getKafka().getUrl());
        props.put("camel.component.kafka.security-protocol", "SASL_SSL");
        props.put("camel.component.kafka.sasl-mechanism", "PLAIN");
        props.put("camel.component.kafka.sasl-jaas-config",
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                "username=" + serviceAccountSpec.getClientId() + " " +
                "password="
                + new String(Base64.getDecoder().decode(serviceAccountSpec.getClientSecret()), StandardCharsets.UTF_8) + ";");

        // always enable supervising route controller, so that camel pods are not killed in case of failure
        // this way we can check it's health and report failing connectors
        props.put("camel.main.route-controller-supervise-enabled", "true");

        // when starting a route (and restarts) fails all attempts then we can control whether the route
        // should influence the health-check and report the route as either UNKNOWN or DOWN. Setting this
        // option to true will report it as DOWN otherwise its UNKNOWN
        props.put("camel.main.route-controller-unhealthy-on-exhausted", "true");

        // always enable camel health checks so we can monitor the connector
        props.put("camel.main.load-health-checks", "true");
        props.put("camel.health.routesEnabled", "true");
        props.put("camel.health.consumersEnabled", "true");
        props.put("camel.health.registryEnabled", "true");

        if (configuration.routeController() != null) {
            props.put("camel.main.route-controller-backoff-delay", configuration.routeController().backoffDelay());
            props.put("camel.main.route-controller-initial-delay", configuration.routeController().initialDelay());
            props.put("camel.main.route-controller-backoff-multiplier", configuration.routeController().backoffMultiplier());
            props.put("camel.main.route-controller-backoff-max-attempts", configuration.routeController().backoffMaxAttempts());
        }

        if (configuration.exchangePooling() != null) {
            props.put(
                "camel.main.exchange-factory",
                configuration.exchangePooling().exchangeFactory());
            props.put(
                "camel.main.exchange-factory-capacity",
                configuration.exchangePooling().exchangeFactoryCapacity());
            props.put(
                "camel.main.exchange-factory-statistics-enabled",
                configuration.exchangePooling().exchangeFactoryStatisticsEnabled());
        }
        return props;
    }

    private ServiceAccountSpec extractServiceAccountSpec(Secret serviceAccountSecret) {
        final ServiceAccount serviceAccountSettings = extract(
            serviceAccountSecret,
            SECRET_ENTRY_SERVICE_ACCOUNT,
            ServiceAccount.class);
        LOGGER.debug("Extracted serviceAccount {}",
            serviceAccountSettings == null ? "is null" : "with clientId: " + serviceAccountSettings.getClientId());

        return serviceAccountSettings == null
            ? new ServiceAccountSpecBuilder().build()
            : new ServiceAccountSpecBuilder()
                .withClientId(serviceAccountSettings.getClientId())
                .withClientSecret(serviceAccountSettings.getClientSecret())
                .build();
    }

    private Secret fetchServiceAccountSecret(ManagedProcessor processor) {
        return kubernetesClient.secrets()
            .inNamespace(processor.getMetadata().getNamespace())
            .withName(processor.getSpec().getSecret())
            .get();
    }

    public static String getIntegrationSecretName(ManagedProcessor processor) {
        return processor.getMetadata().getName() + Resources.PROCESSOR_SECRET_SUFFIX;
    }
}
