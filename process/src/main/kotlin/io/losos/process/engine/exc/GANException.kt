package io.losos.process.engine.exc

import java.lang.RuntimeException


open class GANException: RuntimeException {
    constructor(cause: Throwable): super(cause)
    constructor(cause: String): super(cause)
}

class ActionException(cause: Throwable): GANException(cause)
class WorkException(cause: Throwable): GANException(cause)