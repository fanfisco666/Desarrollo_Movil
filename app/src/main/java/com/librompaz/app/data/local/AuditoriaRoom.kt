package com.librompaz.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Fila de la tabla `auditoria` (RN-02). El perfil se guarda como texto (nombre del enum). */
@Entity(tableName = "auditoria")
data class AuditoriaEntity(
    @PrimaryKey val id: String,
    val usuario: String,
    val perfil: String,
    val accion: String,
    val detalle: String,
    val fechaHora: String,
)

/** DAO de auditoría: solo @Insert y consultas, sin @Update ni @Delete (inalterable, RN-02). */
@Dao
interface AuditoriaDao {

    @Query("SELECT * FROM auditoria ORDER BY fechaHora DESC, id DESC")
    fun observarTodas(): Flow<List<AuditoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(registro: AuditoriaEntity)
}
