package botkin.ai.datamodel

import botkin.ai.NitriteManager
import botkin.ai.Context
import org.dizitart.no2.objects.ObjectRepository
import org.springframework.stereotype.Component

class Quarantine(val id:String,
                 val studyUID:String,
                 val seriaUID:String,
                 val target:String,
                 val reason:String,
                 val workerType:String)

interface QuarantineDao{
    fun createQuarantineReport(quarantine: Quarantine)
    fun getAll():List<Quarantine>
}

@Component
class QuarantineDaoImpl(private val quarantineRepository:ObjectRepository<Quarantine>
        = NitriteManager.getDBInstance(Context()).getRepository(
        Quarantine::class.java)): QuarantineDao {
    override fun getAll(): List<Quarantine> {
        return quarantineRepository.find().toList()
    }

    override fun createQuarantineReport(quarantine: Quarantine) {
        quarantineRepository.insert(quarantine)
    }


}


val jobs:ObjectRepository<Job> = NitriteManager.getDBInstance(Context()).getRepository(
    Job::class.java)