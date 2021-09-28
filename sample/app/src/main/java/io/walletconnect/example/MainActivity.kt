package io.walletconnect.example

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.TextView
import io.walletconnect.example.databinding.ScreenMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.walletconnect.Session
import org.walletconnect.nullOnThrow


class MainActivity : Activity(), Session.Callback {

    private var mTxRequest: Long? = null
    private val mUiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var mScreenMainBinding: ScreenMainBinding

    override fun onStatus(status: Session.Status) {
        when(status) {
            Session.Status.Approved -> adaptUIAfterSessionApproved()
            Session.Status.Closed -> adaptUIAfterSessionClosed()
            Session.Status.Connected -> {
                requestConnectionToWallet()
            }
            Session.Status.Disconnected -> {
                Log.e("+++", "Disconnected")
            }
            is Session.Status.Error -> {
                Log.e("+++", "Error:" + status.throwable.localizedMessage)
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

    private fun adaptUIAfterSessionApproved() {
        mUiScope.launch {
            mScreenMainBinding.screenMainStatus.text = "Connected: ${ExampleApplication.session.approvedAccounts()}"
            mScreenMainBinding.screenMainConnectButton.visibility = View.GONE
            mScreenMainBinding.screenMainDisconnectButton.visibility = View.VISIBLE
            mScreenMainBinding.screenMainTxButton.visibility = View.VISIBLE
        }
    }

    private fun adaptUIAfterSessionClosed() {
        mUiScope.launch {
            mScreenMainBinding.screenMainStatus.text = "Disconnected"
            mScreenMainBinding.screenMainConnectButton.visibility = View.VISIBLE
            mScreenMainBinding.screenMainDisconnectButton.visibility = View.GONE
            mScreenMainBinding.screenMainTxButton.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mScreenMainBinding = ScreenMainBinding.inflate(layoutInflater)
        setContentView(mScreenMainBinding.root)
    }

    override fun onStart() {
        super.onStart()
        initialSetup()
        mScreenMainBinding.screenMainConnectButton.setOnClickListener {
            ExampleApplication.resetSession()
            ExampleApplication.session.addCallback(this)
        }
        mScreenMainBinding.screenMainDisconnectButton.setOnClickListener {
            ExampleApplication.session.kill()
        }
        mScreenMainBinding.screenMainTxButton.setOnClickListener {
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
            this.mTxRequest = txRequest
            navigateToWallet()
        }
    }

    private fun initialSetup() {
        // if ExampleApplication.session is not initialized then return
        val session = nullOnThrow { ExampleApplication.session } ?: return

        session.addCallback(this)
        adaptUIAfterSessionApproved()
    }

    private fun handleResponse(resp: Session.MethodCall.Response) {
        if (resp.id == mTxRequest) {
            mTxRequest = null
            mUiScope.launch {
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
