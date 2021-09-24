package io.walletconnect.example

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.walletconnect.Session
import org.walletconnect.nullOnThrow


class MainActivity : Activity(), Session.Callback {

    private var txRequest: Long? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    override fun onStatus(status: Session.Status) {
        when(status) {
            Session.Status.Approved -> sessionApproved()
            Session.Status.Closed -> sessionClosed()
            Session.Status.Connected -> {
                requestConnectionToWallet()
            }
            Session.Status.Disconnected,
            is Session.Status.Error -> {
                // Do Stuff
            }
        }
    }

    override fun onMethodCall(call: Session.MethodCall) {
    }

    private fun requestConnectionToWallet() {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(ExampleApplication.config.toWCUri())
        startActivity(i)
    }

    private fun navigateToWallet() {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse("wc:")
        startActivity(i)
    }

    private fun sessionApproved() {
        uiScope.launch {
            findViewById<TextView>(R.id.screen_main_status).text = "Connected: ${ExampleApplication.session.approvedAccounts()}"
            findViewById<Button>(R.id.screen_main_connect_button).visibility = View.GONE
            findViewById<Button>(R.id.screen_main_disconnect_button).visibility = View.VISIBLE
            findViewById<Button>(R.id.screen_main_tx_button).visibility = View.VISIBLE
        }
    }

    private fun sessionClosed() {
        uiScope.launch {
            findViewById<TextView>(R.id.screen_main_status).text = "Disconnected"
            findViewById<Button>(R.id.screen_main_connect_button).visibility = View.VISIBLE
            findViewById<Button>(R.id.screen_main_disconnect_button).visibility = View.GONE
            findViewById<Button>(R.id.screen_main_tx_button).visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_main)
    }

    override fun onStart() {
        super.onStart()
        initialSetup()
        findViewById<Button>(R.id.screen_main_connect_button).setOnClickListener {
            ExampleApplication.resetSession()
            ExampleApplication.session.addCallback(this)
        }
        findViewById<Button>(R.id.screen_main_disconnect_button).setOnClickListener {
            ExampleApplication.session.kill()
        }
        findViewById<Button>(R.id.screen_main_tx_button).setOnClickListener {
            val from = ExampleApplication.session.approvedAccounts()?.first()
                    ?: return@setOnClickListener
            val txRequest = System.currentTimeMillis()
            ExampleApplication.session.performMethodCall(
                    Session.MethodCall.SendTransaction(
                            txRequest,
                            from,
                            "0x24EdA4f7d0c466cc60302b9b5e9275544E5ba552",
                            null,
                            null,
                            null,
                            "0x5AF3107A4000",
                            ""
                    ),
                    ::handleResponse
            )
            this.txRequest = txRequest
            navigateToWallet()
        }
    }

    private fun initialSetup() {
        val session = nullOnThrow { ExampleApplication.session } ?: return
        session.addCallback(this)
        sessionApproved()
    }

    private fun handleResponse(resp: Session.MethodCall.Response) {
        if (resp.id == txRequest) {
            txRequest = null
            uiScope.launch {
                val textView = findViewById<TextView>(R.id.screen_main_response)
                textView.visibility = View.VISIBLE
                textView.text = "Last response: " + ((resp.result as? String) ?: "Unknown response")
            }
        }
    }

    override fun onDestroy() {
        ExampleApplication.session.removeCallback(this)
        super.onDestroy()
    }
}
