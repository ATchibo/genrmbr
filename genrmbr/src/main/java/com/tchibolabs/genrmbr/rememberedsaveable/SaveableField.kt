package com.tchibolabs.genrmbr.rememberedsaveable

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class SaveableField(val key: String)