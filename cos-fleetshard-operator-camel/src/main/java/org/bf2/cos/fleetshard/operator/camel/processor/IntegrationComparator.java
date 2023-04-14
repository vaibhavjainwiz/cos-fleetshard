package org.bf2.cos.fleetshard.operator.camel.processor;

import javax.enterprise.context.ApplicationScoped;

import org.bf2.cos.fleetshard.operator.camel.model.Integration;
import org.bf2.cos.fleetshard.operator.support.Comparator;

@ApplicationScoped
public class IntegrationComparator implements Comparator<Integration> {

    @Override
    public boolean compare(Integration requestedResource, Integration deployedResource) {
        return requestedResource.getSpec().equals(deployedResource.getSpec());
    }
}
