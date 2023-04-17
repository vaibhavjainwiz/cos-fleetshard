package org.bf2.cos.fleetshard.operator.camel.processor;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.support.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.bf2.cos.fleetshard.operator.camel.processor.ProcessorConditions.hasCondition;
import static org.bf2.cos.fleetshard.operator.camel.processor.ProcessorConditions.setCondition;

@ApplicationScoped
public class ProcessorSecretValidator {

    @Inject
    KubernetesClient kubernetesClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorSecretValidator.class);

    public UpdateControl<ManagedProcessor> validateProcessorSecret(ManagedProcessor processor) {
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
        return null;
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

            return UpdateControl.patchStatus(processor);
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

            return UpdateControl.patchStatus(processor);
        } else {
            return UpdateControl.<ManagedProcessor> noUpdate().rescheduleAfter(1500, TimeUnit.MILLISECONDS);
        }
    }
}
