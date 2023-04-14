package org.bf2.cos.fleetshard.operator.camel.processor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleet.manager.model.ServiceAccount;
import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ServiceAccountSpec;
import org.bf2.cos.fleetshard.api.ServiceAccountSpecBuilder;
import org.bf2.cos.fleetshard.operator.camel.CamelOperandConfiguration;
import org.bf2.cos.fleetshard.operator.camel.model.Integration;
import org.bf2.cos.fleetshard.operator.camel.model.IntegrationSpec;
import org.bf2.cos.fleetshard.support.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.utils.Serialization;

import static org.bf2.cos.fleetshard.operator.camel.CamelConstants.APPLICATION_PROPERTIES;
import static org.bf2.cos.fleetshard.support.CollectionUtils.asBytesBase64;
import static org.bf2.cos.fleetshard.support.resources.Secrets.SECRET_ENTRY_SERVICE_ACCOUNT;
import static org.bf2.cos.fleetshard.support.resources.Secrets.extract;

@ApplicationScoped
public class ReifyProcessorController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReifyProcessorController.class);

    @Inject
    CamelOperandConfiguration configuration;

    public List<HasMetadata> reify(ManagedProcessor processor, Secret serviceAccountSecret) {
        LOGGER.debug("Reifying connector: {} and secret.metadata: {}", processor, serviceAccountSecret.getMetadata());

        final Map<String, String> properties = createSecretsData(processor, serviceAccountSecret, configuration);

        final Secret secret = new Secret();
        secret.setMetadata(new ObjectMeta());
        secret.getMetadata().setName(processor.getMetadata().getName() + Resources.PROCESSOR_SECRET_SUFFIX);
        secret.setData(Map.of(APPLICATION_PROPERTIES, asBytesBase64(properties)));

        ArrayNode flows = createIntegrationFlows(processor);
        ObjectNode traits = createIntegrationTraits(secret);

        final Integration integration = new Integration();
        integration.setMetadata(new ObjectMeta());
        integration.getMetadata().setName(processor.getMetadata().getName());
        integration.getMetadata().setAnnotations(new TreeMap<>());
        integration.getMetadata().setLabels(new TreeMap<>());
        integration.setSpec(new IntegrationSpec());
        integration.getSpec().setFlows(flows);
        integration.getSpec().setTraits(traits);

        return List.of(secret, integration);
    }

    private ArrayNode createIntegrationFlows(ManagedProcessor processor) {
        ArrayNode flows = Serialization.jsonMapper().createArrayNode();
        flows.add(processor.getSpec().getDefinition());
        return flows;
    }

    private ObjectNode createIntegrationTraits(Secret processorSecret) {
        ObjectNode traits = Serialization.jsonMapper().createObjectNode();
        ObjectNode mount = traits.putObject("mount");
        ArrayNode configs = mount.withArray("configs");
        configs.add("secret:" + processorSecret.getMetadata().getName());
        return traits;
    }

    private Map<String, String> createSecretsData(ManagedProcessor processor, Secret serviceAccountSecret,
        CamelOperandConfiguration cfg) {
        final Map<String, String> props = new TreeMap<>();

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

        if (cfg.routeController() != null) {
            props.put("camel.main.route-controller-backoff-delay", cfg.routeController().backoffDelay());
            props.put("camel.main.route-controller-initial-delay", cfg.routeController().initialDelay());
            props.put("camel.main.route-controller-backoff-multiplier", cfg.routeController().backoffMultiplier());
            props.put("camel.main.route-controller-backoff-max-attempts", cfg.routeController().backoffMaxAttempts());
        }

        if (cfg.exchangePooling() != null) {
            props.put(
                "camel.main.exchange-factory",
                cfg.exchangePooling().exchangeFactory());
            props.put(
                "camel.main.exchange-factory-capacity",
                cfg.exchangePooling().exchangeFactoryCapacity());
            props.put(
                "camel.main.exchange-factory-statistics-enabled",
                cfg.exchangePooling().exchangeFactoryStatisticsEnabled());
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

}
