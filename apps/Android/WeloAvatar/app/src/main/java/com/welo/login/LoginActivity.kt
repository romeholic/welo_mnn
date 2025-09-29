package com.welo.login

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ActivityLoginBinding
import com.welo.base.BaseActivity
import com.welo.entity.UserInfo
import com.welo.viewmodel.AgentViewModel


class LoginActivity : BaseActivity<ActivityLoginBinding, LoginViewModel>() {

    private val agentModel: AgentViewModel by lazy {
        AgentViewModel()
    }
    override fun createBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    @SuppressLint("ServiceCast")
    override fun initView() {
        val username = viewBinding.username
        val password = viewBinding.password
        val login = viewBinding.login
        val loading = viewBinding.loading
//        val register = viewBinding.register


        viewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid
//            register?.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        viewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)
            }
            setResult(RESULT_OK)
//            if (loginResult.success?.username!!.isEmpty()){
//                finish()
//            }
        })

        username.afterTextChanged {
            viewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        username.apply {
            // 添加焦点监听
            setOnClickListener {
                postDelayed({
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
        }
        password.apply {
            afterTextChanged {
                viewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        viewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE
                viewModel.login(username.text.toString(), password.text.toString())
            }
//            register!!.setOnClickListener {
//                loading.visibility = View.VISIBLE
//                viewModel.register(username.text.toString(), password.text.toString())
//            }
//            viewBinding.agent?.setOnClickListener {
//                agentModel.getAgent()
//            }
        }
    }

    override fun observeViewModel() {

    }

    private fun updateUiWithUser(model: UserInfo) {
        val welcome = getString(R.string.welcome)
        val displayName = model.username
        Toast.makeText(
            applicationContext,
            "$welcome $displayName",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}