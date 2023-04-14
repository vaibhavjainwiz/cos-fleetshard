package org.bf2.cos.fleetshard.operator.camel.processor;

import org.bf2.cos.fleetshard.api.ManagedProcessor;
import org.bf2.cos.fleetshard.operator.util.ConditionUtil;

import io.fabric8.kubernetes.api.model.Condition;

public class ProcessorConditions {

    public static void clearConditions(ManagedProcessor processor) {
        ConditionUtil.clearConditions(processor.getStatus().getConditions());
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status, String reason, String message) {
        return ConditionUtil.setCondition(processor.getStatus().getConditions(), type.name(), status.name(), reason, message);
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type, boolean status, String reason,
        String message) {
        return setCondition(processor, type, status ? ProcessorConditions.Status.True : ProcessorConditions.Status.False,
            reason, message);
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status, String reason) {
        return setCondition(processor, type, status, reason, reason);
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type, boolean status,
        String reason) {
        return setCondition(processor, type, status ? ProcessorConditions.Status.True : ProcessorConditions.Status.False,
            reason, reason);
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status) {
        return setCondition(processor, type, status, type.name(), type.name());
    }

    public static boolean setCondition(ManagedProcessor processor, ProcessorConditions.Type type, boolean status) {
        return setCondition(processor, type, status ? ProcessorConditions.Status.True : ProcessorConditions.Status.False);
    }

    public static boolean setCondition(ManagedProcessor processor, Condition condition) {
        return ConditionUtil.setCondition(processor.getStatus().getConditions(), condition);
    }

    public static boolean hasCondition(ManagedProcessor processor, ProcessorConditions.Type type) {
        return ConditionUtil.hasCondition(processor.getStatus().getConditions(), type.name());
    }

    public static boolean hasCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status) {
        return ConditionUtil.hasCondition(processor.getStatus().getConditions(), type.name(), status.name());
    }

    public static boolean hasCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status, String reason) {
        return ConditionUtil.hasCondition(processor.getStatus().getConditions(), type.name(), status.name(), reason);
    }

    public static boolean hasCondition(ManagedProcessor processor, ProcessorConditions.Type type,
        ProcessorConditions.Status status, String reason, String message) {
        return ConditionUtil.hasCondition(processor.getStatus().getConditions(), type.name(), status.name(), reason, message);
    }

    public enum Type {
        Error,
        Ready,
        Initialization,
        Augmentation,
        Monitor,
        Deleting,
        Deleted,
        Stop,
        Stopping,
        Migrate,
        Resync,
    }

    public enum Status {
        True,
        False,
        Unknown
    }
}
