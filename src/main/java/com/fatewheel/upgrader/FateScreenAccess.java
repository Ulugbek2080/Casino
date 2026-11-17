package com.fatewheel.upgrader;

/** Доступ к приватным полям UpgraderScreen. Реализуется mixin-ом UpgraderScreenMixin. */
public interface FateScreenAccess {
    long fatewheel$inputValue();

    long fatewheel$targetValue();

    boolean fatewheel$spinning();

    boolean fatewheel$pickerOpen();
}
