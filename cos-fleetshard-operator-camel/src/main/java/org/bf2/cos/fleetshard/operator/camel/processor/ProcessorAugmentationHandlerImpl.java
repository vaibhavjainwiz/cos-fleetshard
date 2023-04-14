package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedConnectorOperator;
import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ManagedProcessorSpec;
import org.bf2.cos.fleetshard.api.ManagedProcessorStatus;
import org.bf2.cos.fleetshard.operator.FleetShardOperatorConfig;
import org.bf2.cos.fleetshard.support.exceptions.WrappedRuntimeException;
import org.bf2.cos.fleetshard.support.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.bf2.cos.fleetshard.api.ManagedConnector.STATE_FAILED;
import static org.bf2.cos.fleetshard.operator.camel.processor.ProcessorConditions.hasCondition;
import static org.bf2.cos.fleetshard.operator.camel.processor.ProcessorConditions.setCondition;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_CLUSTER_ID;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_CONNECTOR_OPERATOR;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_DEPLOYMENT_ID;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_DEPLOYMENT_RESOURCE_VERSION;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_COMPONENT;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_CREATED_BY;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_INSTANCE;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_MANAGED_BY;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_NAME;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_PART_OF;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_KUBERNETES_VERSION;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_OPERATOR_OWNER;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_OPERATOR_TYPE;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_PROCESSOR_ID;
import static org.bf2.cos.fleetshard.support.resources.Resources.LABEL_PROCESSOR_TYPE_ID;
import static org.bf2.cos.fleetshard.support.resources.Resources.copyAnnotation;
import static org.bf2.cos.fleetshard.support.resources.Resources.copyLabel;

@ApplicationScoped
public class ProcessorAugmentationHandlerImpl implements ProcessorAugmentationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorAugmentationHandlerImpl.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    ManagedConnectorOperator managedConnectorOperator;

    @Inject
    FleetShardOperatorConfig config;

    @Inject
    ReifyProcessorController reifyProcessorController;

    @Override
    public UpdateControl<ManagedProcessor> handleAugmentation(ManagedProcessor processor) {
        if (processor.getSpec().getSecret() == null) {
            LOGGER.info("Secret for processor not defines");
            return UpdateControl.noUpdate();
        }
        Secret secret = kubernetesClient.secrets()
            .inNamespace(processor.getMetadata().getNamespace())
            .withName(processor.getSpec().getSecret())
            .get();

        if (secret == null) {
            return resolveSecretNotExistsCondition(processor);
        } else {
            String processorUow = processor.getSpec().getUnitOfWork();
            String secretUow = secret.getMetadata().getLabels().get(Resources.LABEL_UOW);
            if (!Objects.equals(processorUow, secretUow)) {
                return resolveSecretUowMismatchCondition(processor, processorUow, secretUow);
            }
        }

        List<HasMetadata> resources;
        try {
            resources = reifyProcessorController.reify(processor, secret);
        } catch (Exception e) {
            LOGGER.warn("Error reifying deployment {}", processor.getSpec().getDeploymentId(), e);
            return resolveReifyFailedCondition(processor, e);
        }

        for (var resource : resources) {
            appendLabels(processor, resource);
            appendAnnotations(processor, resource);
            appendOwnerReference(processor, resource);

            var result = kubernetesClient.resource(resource)
                .inNamespace(processor.getMetadata().getNamespace())
                .createOrReplace();

            LOGGER.debug("Resource {}:{}:{}@{} updated/created",
                result.getApiVersion(),
                result.getKind(),
                result.getMetadata().getName(),
                result.getMetadata().getNamespace());
        }

        return resolveAugmentationSuccessfulCondition(processor);
    }

    private UpdateControl<ManagedProcessor> resolveSecretNotExistsCondition(ManagedProcessor processor) {
        boolean retry = hasCondition(
            processor,
            ProcessorConditions.Type.Augmentation,
            ProcessorConditions.Status.False,
            "SecretNotFound");

        if (!retry) {
            LOGGER.debug(
                "Unable to find secret with name: {}", processor.getSpec().getSecret());

            setCondition(
                processor,
                ProcessorConditions.Type.Augmentation,
                ProcessorConditions.Status.False,
                "SecretNotFound",
                "Unable to find secret with name: " + processor.getSpec().getSecret());
            setCondition(
                processor,
                ProcessorConditions.Type.Ready,
                ProcessorConditions.Status.False,
                "AugmentationError",
                "AugmentationError");

            return UpdateControl.updateStatus(processor);
        } else {
            return UpdateControl.<ManagedProcessor> noUpdate().rescheduleAfter(1500, TimeUnit.MILLISECONDS);
        }
    }

    private UpdateControl<ManagedProcessor> resolveSecretUowMismatchCondition(ManagedProcessor processor, String processorUow,
        String secretUow) {
        boolean retry = hasCondition(
            processor,
            ProcessorConditions.Type.Augmentation,
            ProcessorConditions.Status.False,
            "SecretUoWMismatch");

        if (!retry) {
            LOGGER.debug(
                "Secret and Connector UoW mismatch (processor: {}, secret: {})", processorUow, secretUow);

            setCondition(
                processor,
                ProcessorConditions.Type.Augmentation,
                ProcessorConditions.Status.False,
                "SecretUoWMismatch",
                "Secret and Connector UoW mismatch (processor: " + processorUow + ", secret: " + secretUow + ")");
            setCondition(
                processor,
                ProcessorConditions.Type.Ready,
                ProcessorConditions.Status.False,
                "AugmentationError",
                "AugmentationError");

            return UpdateControl.updateStatus(processor);
        } else {
            return UpdateControl.<ManagedProcessor> noUpdate().rescheduleAfter(1500, TimeUnit.MILLISECONDS);
        }
    }

    private UpdateControl<ManagedProcessor> resolveReifyFailedCondition(ManagedProcessor processor, Exception e) {
        setCondition(
            processor,
            ProcessorConditions.Type.Augmentation,
            ProcessorConditions.Status.False,
            "ReifyFailed",
            e instanceof WrappedRuntimeException ? e.getCause().getMessage() : e.getMessage());
        setCondition(
            processor,
            ProcessorConditions.Type.Stopping,
            ProcessorConditions.Status.True,
            "Stopping",
            "Stopping");

        processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Stopping);
        processor.getStatus().getProcessorStatus().setPhase(STATE_FAILED);
        processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());

        return UpdateControl.updateStatus(processor);
    }

    private UpdateControl<ManagedProcessor> resolveAugmentationSuccessfulCondition(ManagedProcessor processor) {
        processor.getStatus().setPhase(ManagedProcessorStatus.PhaseType.Monitor);
        processor.getStatus().getProcessorStatus().setConditions(Collections.emptyList());

        setCondition(processor, ProcessorConditions.Type.Resync, false);
        setCondition(processor, ProcessorConditions.Type.Monitor, true);
        setCondition(processor, ProcessorConditions.Type.Ready, true);
        setCondition(processor, ProcessorConditions.Type.Augmentation, true);

        return UpdateControl.updateStatus(processor);
    }

    private void appendLabels(ManagedProcessor processor, HasMetadata resource) {
        if (resource.getMetadata().getLabels() == null) {
            resource.getMetadata().setLabels(new HashMap<>());
        }

        ManagedProcessorSpec spec = processor.getSpec();
        final String rv = String.valueOf(spec.getDeploymentResourceVersion());

        final Map<String, String> labels = KubernetesResourceUtil.getOrCreateLabels(resource);
        labels.put(LABEL_CONNECTOR_OPERATOR, processor.getStatus().getProcessorStatus().getAssignedOperator().getId());
        labels.put(LABEL_PROCESSOR_ID, spec.getProcessorId());
        labels.put(LABEL_PROCESSOR_TYPE_ID, spec.getProcessorTypeId());
        labels.put(LABEL_DEPLOYMENT_ID, spec.getDeploymentId());
        labels.put(LABEL_CLUSTER_ID, spec.getClusterId());
        labels.put(LABEL_OPERATOR_TYPE, managedConnectorOperator.getSpec().getType());
        labels.put(LABEL_OPERATOR_OWNER, managedConnectorOperator.getMetadata().getName());
        labels.put(LABEL_DEPLOYMENT_RESOURCE_VERSION, rv);

        // Kubernetes recommended labels
        labels.put(LABEL_KUBERNETES_NAME, spec.getProcessorId());
        labels.put(LABEL_KUBERNETES_INSTANCE, spec.getDeploymentId());
        labels.put(LABEL_KUBERNETES_VERSION, rv);
        labels.put(LABEL_KUBERNETES_COMPONENT, Resources.COMPONENT_PROCESSOR);
        labels.put(LABEL_KUBERNETES_PART_OF, spec.getClusterId());
        labels.put(LABEL_KUBERNETES_MANAGED_BY, managedConnectorOperator.getMetadata().getName());
        labels.put(LABEL_KUBERNETES_CREATED_BY, managedConnectorOperator.getMetadata().getName());

        config.processors().targetLabels().ifPresent(items -> {
            for (String item : items) {
                copyLabel(item, processor, resource);
            }
        });
    }

    private void appendAnnotations(ManagedProcessor processor, HasMetadata resource) {
        if (resource.getMetadata().getAnnotations() == null) {
            resource.getMetadata().setAnnotations(new HashMap<>());
        }

        config.processors().targetAnnotations().ifPresent(items -> {
            for (String item : items) {
                copyAnnotation(item, processor, resource);
            }
        });
    }

    private void appendOwnerReference(ManagedProcessor processor, HasMetadata resource) {
        resource.getMetadata().setOwnerReferences(List.of(
            new OwnerReferenceBuilder()
                .withApiVersion(processor.getApiVersion())
                .withKind(processor.getKind())
                .withName(processor.getMetadata().getName())
                .withUid(processor.getMetadata().getUid())
                .withAdditionalProperties(Map.of("namespace", processor.getMetadata().getNamespace()))
                .withBlockOwnerDeletion(true)
                .build()));
    }
}
