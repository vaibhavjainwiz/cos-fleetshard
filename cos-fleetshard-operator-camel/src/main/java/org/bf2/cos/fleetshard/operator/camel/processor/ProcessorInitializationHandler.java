package org.bf2.cos.fleetshard.operator.camel.processor;

import org.bf2.cos.fleetshard.api.ManagedProcessor;

public interface ProcessorInitializationHandler {

    void handleInitialization(ManagedProcessor processor);
}
