package com.roshank8s.androidterminal.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.roshank8s.androidterminal.R
import com.roshank8s.androidterminal.terminal.TerminalSession

/**
 * RecyclerView adapter for displaying terminal session tabs in the navigation drawer.
 */
class SessionsAdapter(
    private val sessions: List<TerminalSession>,
    private val listener: SessionActionListener
) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

    interface SessionActionListener {
        fun onSessionSelected(session: TerminalSession)
        fun onSessionClose(session: TerminalSession)
    }

    var activeSessionId: String? = null

    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.session_title)
        val subtitleText: TextView = view.findViewById(R.id.session_subtitle)
        val closeButton: ImageButton = view.findViewById(R.id.session_close)
        val statusIndicator: View = view.findViewById(R.id.session_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        val isActive = session.id == activeSessionId

        holder.titleText.text = session.title.ifEmpty { "Session ${position + 1}" }
        holder.subtitleText.text = if (session.isFinished) {
            "Exited (${session.exitStatus})"
        } else {
            "PID: ${session.pid}"
        }

        // Visual active state
        holder.itemView.isActivated = isActive
        holder.statusIndicator.setBackgroundResource(
            if (session.isFinished) R.drawable.status_finished
            else if (isActive) R.drawable.status_active
            else R.drawable.status_inactive
        )

        holder.itemView.setOnClickListener {
            listener.onSessionSelected(session)
        }

        holder.closeButton.setOnClickListener {
            listener.onSessionClose(session)
        }
    }

    override fun getItemCount(): Int = sessions.size
}
