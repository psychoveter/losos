package ai.botkin.satellite.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component


class MlFunctionDuration(val fct:Long = 15/60 * 1000, val dx:Long = 7/60 * 1000)