package com.idevicesinc.sweetblue;

<<<<<<< HEAD
import com.idevicesinc.sweetblue.utils.State;
=======
import com.idevicesinc.sweetblue.utils.BitwiseEnum;
>>>>>>> b406005... Source control mgmt

public class P_ServerStateTracker extends PA_StateTracker {

	private BleServer.StateListener m_stateListener;
	private final BleServer m_server;
	
	P_ServerStateTracker(BleServer server)
	{
<<<<<<< HEAD
		super(server.getManager().getLogger(), BleDeviceState.values());
		
		m_server = server;
		
		set(E_Intent.IMPLICIT, BleDeviceState.UNDISCOVERED, true, BleDeviceState.DISCONNECTED, true);
=======
		super(server.getManager().getLogger());
		
		m_server = server;
		
		set(BleDeviceState.UNDISCOVERED, true, BleDeviceState.DISCONNECTED, true);
>>>>>>> b406005... Source control mgmt
	}
	
	public void setListener(BleServer.StateListener listener)
	{
		if( listener != null )
		{
			m_stateListener = new P_WrappingServerStateListener(listener, m_server.getManager().m_mainThreadHandler, m_server.getManager().m_config.postCallbacksToMainThread);
		}
		else
		{
			m_stateListener = null;
		}
	}

<<<<<<< HEAD
	@Override protected void onStateChange(int oldStateBits, int newStateBits, int intentMask)
=======
	@Override protected void onStateChange(int oldStateBits, int newStateBits)
>>>>>>> b406005... Source control mgmt
	{
		if( m_stateListener != null )
		{
			m_stateListener.onStateChange(m_server, oldStateBits, newStateBits);
		}
		
		if( m_server.getManager().m_defaultServerStateListener != null )
		{
			m_server.getManager().m_defaultServerStateListener.onStateChange(m_server, oldStateBits, newStateBits);
		}
		
//		m_device.getManager().getLogger().e(this.toString());
	}

<<<<<<< HEAD
	@Override protected void append_assert(State newState)
=======
	@Override protected void append_assert(BitwiseEnum newState)
>>>>>>> b406005... Source control mgmt
	{
		if( newState.ordinal() > BleDeviceState.CONNECTING.ordinal() )
		{
			//--- DRK > No longer valid...during the connection flow a rogue disconnect can come in.
			//---		This immediately changes the native state of the device but the actual callback
			//---		for the disconnect is sent to the update thread so for a brief time we can be
			//---		abstractly connected/connecting but actually not natively connected. 
//			m_device.getManager().ASSERT(m_device.m_nativeWrapper.isNativelyConnected());
		}
	}
	
	@Override public String toString()
	{
		return super.toString(BleDeviceState.values());
	}
}
