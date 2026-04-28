package com.example.gab.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_session")

object SessionKeys {
    val USER_ID    = intPreferencesKey("user_id")
    val USER_NAME  = stringPreferencesKey("user_name")
    val USER_ROLE  = intPreferencesKey("user_role")
    val USER_UNIT  = stringPreferencesKey("user_unit")
    val AUTH_TOKEN = stringPreferencesKey("auth_token")
}

class SessionDataStore(private val context: Context) {

    val userId: Flow<Int?> = context.sessionDataStore.data.map { it[SessionKeys.USER_ID] }
    val userName: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.USER_NAME] }
    val userRole: Flow<Int?> = context.sessionDataStore.data.map { it[SessionKeys.USER_ROLE] }
    val userUnit: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.USER_UNIT] }
    val authToken: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.AUTH_TOKEN] }

    suspend fun saveSession(userId: Int, name: String, role: Int, unit: String, token: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[SessionKeys.USER_ID]    = userId
            prefs[SessionKeys.USER_NAME]  = name
            prefs[SessionKeys.USER_ROLE]  = role
            prefs[SessionKeys.USER_UNIT]  = unit
            prefs[SessionKeys.AUTH_TOKEN] = token
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
