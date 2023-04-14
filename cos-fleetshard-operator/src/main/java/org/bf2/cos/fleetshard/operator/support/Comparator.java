package org.bf2.cos.fleetshard.operator.support;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Comparator<T extends HasMetadata> {

    boolean compare(T requestedResource, T deployedResource);
}
