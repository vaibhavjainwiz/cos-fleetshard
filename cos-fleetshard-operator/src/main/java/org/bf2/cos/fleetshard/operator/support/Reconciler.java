package org.bf2.cos.fleetshard.operator.support;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Reconciler<T extends HasMetadata, C extends HasMetadata> {

    boolean reconcile(T processor);

    Comparator<C> getComparator();
}
