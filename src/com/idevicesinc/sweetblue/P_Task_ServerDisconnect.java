package com.idevicesinc.sweetblue;

public class P_Task_ServerDisconnect extends PA_Task_RequiresBleOn {

    private final BleServer m_server;

	public P_Task_ServerDisconnect( BleServer server, I_StateListener listener ) {
		super( server, listener );
        m_server = server;

    }

	@Override
    protected BleTask getTaskType() {
        return null;
    }

    @Override
    void execute() {

        if (m_server == null) {
			m_logger.w("Already disconnected and server==null!");
			
			redundant();
			
			return;
		}
		
		if( getDevice().m_nativeWrapper./*already*/isNativelyDisconnecting() )
		{
			// nothing to do
			
			return;
		}
        m_server.getNative().cancelConnection(m_server.getDevice().getNative());
    }

	@Override
	public PE_TaskPriority getPriority() {
		return PE_TaskPriority.FOR_EXPLICIT_BONDING_AND_CONNECTING;
	}

}
