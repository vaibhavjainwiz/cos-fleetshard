package org.bf2.cos.fleetshard.operator.support;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

@ApplicationScoped
public class DeltaProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeltaProcessor.class);

    @Inject
    KubernetesClient kubernetesClient;

    public <T extends HasMetadata> boolean processDelta(Comparator<T> comparator, T requestResource, T deployedResource) {

        // resource added
        if (requestResource != null && deployedResource == null) {
            kubernetesClient.resource(requestResource).inNamespace(requestResource.getMetadata().getNamespace()).create();
            LOGGER.debug("New resource added. Resource {}:{}:{}@{}",
                requestResource.getApiVersion(),
                requestResource.getKind(),
                requestResource.getMetadata().getName(),
                requestResource.getMetadata().getNamespace());
            return true;
        }

        // resource deleted
        if (requestResource == null && deployedResource != null) {
            kubernetesClient.resource(deployedResource).inNamespace(deployedResource.getMetadata().getNamespace()).delete();
            LOGGER.debug("Resource deleted. Resource {}:{}:{}@{}",
                deployedResource.getApiVersion(),
                deployedResource.getKind(),
                deployedResource.getMetadata().getName(),
                deployedResource.getMetadata().getNamespace());
            return true;
        }

        // resource modified
        if (requestResource != null) {
            var modified = comparator.compare(requestResource, deployedResource);
            if (modified) {
                kubernetesClient.resource(deployedResource).inNamespace(deployedResource.getMetadata().getNamespace()).patch();
                LOGGER.debug("Resource modified. Resource {}:{}:{}@{}",
                    deployedResource.getApiVersion(),
                    deployedResource.getKind(),
                    deployedResource.getMetadata().getName(),
                    deployedResource.getMetadata().getNamespace());
                return true;
            }
        }
        assert requestResource != null;
        LOGGER.debug("No delta found. Resource {}:{}:{}@{}",
            requestResource.getApiVersion(),
            requestResource.getKind(),
            requestResource.getMetadata().getName(),
            requestResource.getMetadata().getNamespace());
        return false;
    }
}
