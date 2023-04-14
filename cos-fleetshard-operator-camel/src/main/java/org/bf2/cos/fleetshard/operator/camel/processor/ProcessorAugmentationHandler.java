package org.bf2.cos.fleetshard.operator.camel.processor;

import org.bf2.cos.fleetshard.api.ManagedProcessor;

import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public interface ProcessorAugmentationHandler {

    UpdateControl<ManagedProcessor> handleAugmentation(ManagedProcessor processor);
}
