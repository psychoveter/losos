package io.losos.process.engine.exc

import java.lang.RuntimeException

open class GANException(cause: Throwable): RuntimeException(cause)

class ActionException(cause: Throwable): GANException(cause)
class WorkException(cause: Throwable): GANException(cause)