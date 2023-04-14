package org.bf2.cos.fleetshard.operator.camel.processor;

import org.bf2.cos.fleetshard.operator.support.Comparator;

import io.fabric8.kubernetes.api.model.Secret;

public class SecretComparator implements Comparator<Secret> {
    @Override
    public boolean compare(Secret requestedResource, Secret deployedResource) {
        return requestedResource.getData().equals(deployedResource.getData());
    }
}
