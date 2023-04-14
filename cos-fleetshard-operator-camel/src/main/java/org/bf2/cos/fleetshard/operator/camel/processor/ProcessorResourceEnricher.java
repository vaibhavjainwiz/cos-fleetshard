package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedConnectorOperator;
import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.api.ManagedProcessorSpec;
import org.bf2.cos.fleetshard.operator.FleetShardOperatorConfig;
import org.bf2.cos.fleetshard.support.resources.Resources;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;

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
public class ProcessorResourceEnricher {

    @Inject
    ManagedConnectorOperator managedConnectorOperator;

    @Inject
    FleetShardOperatorConfig config;

    public void appendLabels(ManagedProcessor processor, HasMetadata resource) {
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

    public void appendAnnotations(ManagedProcessor processor, HasMetadata resource) {
        if (resource.getMetadata().getAnnotations() == null) {
            resource.getMetadata().setAnnotations(new HashMap<>());
        }

        config.processors().targetAnnotations().ifPresent(items -> {
            for (String item : items) {
                copyAnnotation(item, processor, resource);
            }
        });
    }

    public void appendOwnerReference(ManagedProcessor processor, HasMetadata resource) {
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
