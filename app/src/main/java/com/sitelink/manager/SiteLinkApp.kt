package com.sitelink.manager

import android.app.Application
import com.sitelink.manager.data.config.ServerConfigManager
import com.sitelink.manager.data.db.AppDatabase
import com.sitelink.manager.data.keystore.KeyStoreManager
import com.sitelink.manager.data.repository.SiteRepository
import com.sitelink.manager.data.repository.SshRepository
import com.sitelink.manager.data.ssh.SshManager
import com.sitelink.manager.data.ssh.SshTerminalManager
import com.sitelink.manager.data.ssh.SshTunnelManager

class SiteLinkApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var siteRepository: SiteRepository
        private set
    lateinit var sshRepository: SshRepository
        private set
    lateinit var keyStoreManager: KeyStoreManager
        private set
    lateinit var serverConfigManager: ServerConfigManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        keyStoreManager = KeyStoreManager()
        serverConfigManager = ServerConfigManager(this)

        val sshManager = SshManager(database.trustedHostKeyDao(), serverConfigManager)
        val sshTunnelManager = SshTunnelManager(sshManager, database.connectionLogDao())
        val sshTerminalManager = SshTerminalManager(sshManager, database.connectionLogDao())

        siteRepository = SiteRepository(database.siteDao())
        sshRepository = SshRepository(
            sshManager = sshManager,
            sshTunnelManager = sshTunnelManager,
            sshTerminalManager = sshTerminalManager,
            trustedHostKeyDao = database.trustedHostKeyDao(),
            serverConfigManager = serverConfigManager
        )
    }
}
