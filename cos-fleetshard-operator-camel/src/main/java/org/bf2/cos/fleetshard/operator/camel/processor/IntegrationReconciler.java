package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.TreeMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.operator.camel.model.Integration;
import org.bf2.cos.fleetshard.operator.camel.model.IntegrationSpec;
import org.bf2.cos.fleetshard.operator.support.Comparator;
import org.bf2.cos.fleetshard.operator.support.DeltaProcessor;
import org.bf2.cos.fleetshard.operator.support.Reconciler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;

import static org.bf2.cos.fleetshard.operator.camel.processor.IntegrationSecretReconciler.getIntegrationSecretName;

@ApplicationScoped
public class IntegrationReconciler implements Reconciler<ManagedProcessor, Integration> {

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    ProcessorResourceEnricher processorResourceEnricher;

    @Inject
    DeltaProcessor deltaProcessor;

    @Override
    public boolean reconcile(ManagedProcessor processor) {
        var requestedResource = createRequestedIntegrationResource(processor);
        var deployedResource = fetchDeployedIntegrationResource(processor);
        return deltaProcessor.processDelta(getComparator(), requestedResource, deployedResource);
    }

    @Override
    public Comparator<Integration> getComparator() {
        return new IntegrationComparator();
    }

    private Integration fetchDeployedIntegrationResource(ManagedProcessor processor) {
        return kubernetesClient.resources(Integration.class)
            .inNamespace(processor.getMetadata().getNamespace())
            .withName(processor.getMetadata().getName()).get();
    }

    private Integration createRequestedIntegrationResource(ManagedProcessor processor) {
        ArrayNode flows = createIntegrationFlows(processor);
        ObjectNode traits = createIntegrationTraits(processor);

        final Integration integration = new Integration();
        integration.setMetadata(new ObjectMeta());
        integration.getMetadata().setName(processor.getMetadata().getName());
        integration.getMetadata().setAnnotations(new TreeMap<>());
        integration.getMetadata().setLabels(new TreeMap<>());
        integration.setSpec(new IntegrationSpec());
        integration.getSpec().setFlows(flows);
        integration.getSpec().setTraits(traits);

        processorResourceEnricher.appendLabels(processor, integration);
        processorResourceEnricher.appendAnnotations(processor, integration);
        processorResourceEnricher.appendOwnerReference(processor, integration);
        return integration;
    }

    private ArrayNode createIntegrationFlows(ManagedProcessor processor) {
        ArrayNode flows = Serialization.jsonMapper().createArrayNode();
        flows.add(processor.getSpec().getDefinition());
        return flows;
    }

    private ObjectNode createIntegrationTraits(ManagedProcessor processor) {
        ObjectNode traits = Serialization.jsonMapper().createObjectNode();
        ObjectNode mount = traits.putObject("mount");
        ArrayNode configs = mount.withArray("configs");
        configs.add("secret:" + getIntegrationSecretName(processor));
        return traits;
    }
}
