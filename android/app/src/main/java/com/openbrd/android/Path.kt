package com.openbrd.android



import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

@Entity(tableName = "paths")
data class Path(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    @ColumnInfo(name = "path") val path:String,
    @ColumnInfo(name = "events") val events: String,
) : Serializable

@Dao
interface PathDao {
    @Query("SELECT * FROM paths")
    suspend fun getAll(): List<Path>

    @Query("SELECT * FROM paths ORDER BY events")
    fun getFlow(): Flow<List<Path>>

    @Query("SELECT * FROM paths WHERE uid IN (:pathIds)")
    suspend fun loadAllByIds(pathIds: IntArray): List<Path>

    @Insert
    suspend fun insertAll(vararg paths: Path)

    @Delete
    suspend fun deleteAll(vararg paths: Path)
}
