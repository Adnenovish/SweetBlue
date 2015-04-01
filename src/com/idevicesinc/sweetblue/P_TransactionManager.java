package com.idevicesinc.sweetblue;

import static com.idevicesinc.sweetblue.BleDeviceState.AUTHENTICATED;
import static com.idevicesinc.sweetblue.BleDeviceState.AUTHENTICATING;
import static com.idevicesinc.sweetblue.BleDeviceState.INITIALIZING;
import static com.idevicesinc.sweetblue.BleDeviceState.PERFORMING_OTA;
import android.bluetooth.BluetoothGatt;

import com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.Status;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener;
import com.idevicesinc.sweetblue.BleTransaction.EndReason;
import com.idevicesinc.sweetblue.PA_StateTracker.E_Intent;

class P_TransactionManager
{
	final BleTransaction.PI_EndListener m_txnEndListener = new BleTransaction.PI_EndListener()
	{
		@Override public void onTransactionEnd(BleTransaction txn, EndReason reason, ReadWriteListener.ReadWriteEvent txnFailReason)
		{
			synchronized (m_device.m_threadLock)
			{
				onTransactionEnd_private(txn, reason, txnFailReason);
			}
		}
		
		private void onTransactionEnd_private(BleTransaction txn, EndReason reason, ReadWriteListener.ReadWriteEvent txnFailReason)
		{
			clearQueueLock();

			m_current = null;
			
			if( !m_device.is_internal(BleDeviceState.CONNECTED) )
			{
				if( reason == EndReason.CANCELLED )
				{
					return;
				}
				else if( reason == EndReason.SUCCEEDED || reason == EndReason.FAILED )
				{
					m_device.getManager().ASSERT(false, "nativelyConnected=" + m_device.getManager().getLogger().gattConn(m_device.m_nativeWrapper.getConnectionState()) + " gatt==" + m_device.m_nativeWrapper.getGatt());
					
					return;
				}
			}
			
			if (txn == m_authTxn )
			{
				if (reason == EndReason.SUCCEEDED)
				{
					m_device.getPollManager().enableNotifications();
					
					if ( m_initTxn != null)
					{
						m_device.stateTracker().update
						(
							E_Intent.INTENTIONAL,
							BleStatuses.GATT_STATUS_NOT_APPLICABLE,
							AUTHENTICATING, false, AUTHENTICATED, true, INITIALIZING, true
						);

						start(m_initTxn);
					}
					else
					{
						m_device.onFullyInitialized(BleStatuses.GATT_STATUS_NOT_APPLICABLE);
					}
				}
				else
				{
					m_device.disconnectWithReason(Status.AUTHENTICATION_FAILED, BleDevice.ConnectionFailListener.Timing.NOT_APPLICABLE, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE, txnFailReason);
				}
			}
			else if (txn == m_initTxn )
			{
				if (reason == EndReason.SUCCEEDED)
				{
					m_device.onFullyInitialized(BleStatuses.GATT_STATUS_NOT_APPLICABLE);
				}
				else
				{
					m_device.disconnectWithReason(Status.INITIALIZATION_FAILED, BleDevice.ConnectionFailListener.Timing.NOT_APPLICABLE, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE, txnFailReason);
				}
			}
			else if (txn == m_device.getFirmwareUpdateTxn())
			{
//				m_device.m_txnMngr.clearFirmwareUpdateTxn();
				E_Intent intent = E_Intent.UNINTENTIONAL;
				m_device.stateTracker_main().remove(PERFORMING_OTA, intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE);

				//--- DRK > As of now don't care whether this succeeded or failed.
				if (reason == EndReason.SUCCEEDED)
				{
				}
				else
				{
				}
			}
			else if( txn == m_anonTxn )
			{
				m_anonTxn = null;
			}
		}
	};
	private final Object m_threadLock = new Object();
	
	private final BleDevice m_device;
	
	BleTransaction.Auth m_authTxn;
	BleTransaction.Init m_initTxn;
	BleTransaction.Ota m_firmwareUpdateTxn;
	BleTransaction m_anonTxn;
	
	BleTransaction m_current;
	
	ReadWriteListener.ReadWriteEvent m_failReason;
	
	P_TransactionManager(BleDevice device)
	{
		m_device = device;

		resetReadWriteResult();
	}
	
	void start(final BleTransaction txn)
	{
		synchronized (m_threadLock)
		{
			if( m_current != null )
			{
				m_device.getManager().ASSERT(false, "Old: " + m_current.getClass().getSimpleName() + " New: " + txn.getClass().getSimpleName());
			}
			
			m_current = txn;
			
			start_common(m_device, txn);
		}
	}
	
	static void start_common(final BleDevice device, final BleTransaction txn)
	{
		if( txn.needsAtomicity() )
		{
			device.getManager().getTaskQueue().add(new P_Task_TxnLock(device, txn));
		}
		
		txn.start_internal();
	}
	
	BleTransaction getCurrent()
	{
		return m_current;
	}
	
	void clearQueueLock()
	{
		//--- DRK > Kind of a band-aid hack to prevent deadlock when this is called upstream from
		//---		main thread. A queue addition comes in on the heartbeat thread, which takes the
		//---		queue lock. At the same time we call disconnect from the main thread, which takes
		//---		the device lock, which then cascades here and would wait on the queue lock for
		//---		succeed or clear. Meanwhile queue calls device.equals which used to take device lock.
		//---		It doesn't anymore, but still putting this behind a runnabel just in case.
		m_device.getManager().getUpdateLoop().postIfNeeded(new Runnable()
		{
			@Override public void run()
			{
				if( !m_device.getManager().getTaskQueue().succeed(P_Task_TxnLock.class, m_device) )
				{
					m_device.getManager().getTaskQueue().clearQueueOf(P_Task_TxnLock.class, m_device);
				}
			}
		});
	}
	
//	void clearAllTxns()
//	{
//		synchronized (m_threadLock)
//		{
//			if( m_authTxn != null )
//			{
//				m_authTxn.deinit();
//				m_authTxn = null;
//			}
//			
//			if( m_initTxn != null )
//			{
//				m_initTxn.deinit();
//				m_initTxn = null;
//			}
//			
//			clearFirmwareUpdateTxn();
//			
//			m_current = null;
//		}
//	}
	
//	void clearFirmwareUpdateTxn()
//	{
//		synchronized (m_threadLock)
//		{
//			if( m_firmwareUpdateTxn != null )
//			{
//				m_firmwareUpdateTxn.deinit();
//				m_firmwareUpdateTxn = null;
//			}
//		}
//	}
	
	void cancelFirmwareUpdateTxn()
	{
		synchronized (m_threadLock)
		{
			if( m_firmwareUpdateTxn != null && m_firmwareUpdateTxn.isRunning() )
			{
				m_firmwareUpdateTxn.cancel();
			}
		}
	}
	
	void cancelAllTransactions()
	{
		synchronized (m_threadLock)
		{
			if( m_authTxn != null && m_authTxn.isRunning() )
			{
				m_authTxn.cancel();
			}
			
			if( m_initTxn != null && m_initTxn.isRunning() )
			{
				m_initTxn.cancel();
			}
			
			cancelFirmwareUpdateTxn();
			
			if( m_anonTxn != null && m_anonTxn.isRunning() )
			{
				m_anonTxn.cancel();
				m_anonTxn = null;
			}
			
			if( m_current != null )
			{
				m_device.getManager().ASSERT(false, "Expected current transaction to be null.");
				
				m_current.cancel();
				m_current = null;
			}
		}
	}
	
	void update(double timeStep)
	{
		synchronized (m_threadLock)
		{
			if( m_authTxn != null && m_authTxn.isRunning() )
			{
				m_authTxn.update_internal(timeStep);
			}
			
			if( m_initTxn != null && m_initTxn.isRunning() )
			{
				m_initTxn.update_internal(timeStep);
			}
			
			if( m_firmwareUpdateTxn != null && m_firmwareUpdateTxn.isRunning() )
			{
				m_firmwareUpdateTxn.update_internal(timeStep);
			}
			
			if( m_anonTxn != null && m_anonTxn.isRunning() )
			{
				m_anonTxn.update_internal(timeStep);
			}
		}
	}
	
	void onConnect(BleTransaction.Auth authenticationTxn, BleTransaction.Init initTxn)
	{
		synchronized (m_threadLock)
		{
			m_authTxn = authenticationTxn;
			m_initTxn = initTxn;
			
			if( m_authTxn != null )
			{
				m_authTxn.init(m_device, m_txnEndListener);
			}
			
			if( m_initTxn != null )
			{
				m_initTxn.init(m_device, m_txnEndListener);
			}
		}
	}
	
	private void resetReadWriteResult()
	{
		m_failReason = m_device.NULL_READWRITE_EVENT();
	}
	
	void onReadWriteResult(ReadWriteListener.ReadWriteEvent result)
	{
		resetReadWriteResult();
		
		if( !result.wasSuccess() )
		{
			if( m_device.isAny_internal(AUTHENTICATING, INITIALIZING) )
			{
				m_failReason = result;
			}
		}
	}
	
	void onReadWriteResultCallbacksCalled()
	{
		resetReadWriteResult();
	}
	
	void startOta(BleTransaction.Ota txn)
	{
		synchronized (m_threadLock)
		{
//			m_device.getManager().ASSERT(m_firmwareUpdateTxn == null);
			
			m_firmwareUpdateTxn = txn;
			m_firmwareUpdateTxn.init(m_device, m_txnEndListener);
			
			m_device.stateTracker_main().append(PERFORMING_OTA, E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE);
			
			start(m_firmwareUpdateTxn);
		}
	}
	
	void performAnonTransaction(BleTransaction txn)
	{
		m_anonTxn = txn;
		
		m_anonTxn.init(m_device, m_txnEndListener);
		start(m_anonTxn);
	}
	
	void runAuthOrInitTxnIfNeeded(final int gattStatus, Object ... extraFlags)
	{
		synchronized (m_threadLock)
		{
			E_Intent intent = m_device.lastConnectDisconnectIntent();
			if( m_authTxn == null && m_initTxn == null )
			{
				m_device.getPollManager().enableNotifications();
				
				m_device.onFullyInitialized(gattStatus, extraFlags);
			}
			else if( m_authTxn != null )
			{
				m_device.stateTracker().update(intent, BluetoothGatt.GATT_SUCCESS, extraFlags, AUTHENTICATING, true);
				
				start(m_authTxn);
			}
			else if( m_initTxn != null )
			{
				m_device.getPollManager().enableNotifications();
				
				m_device.stateTracker().update(intent, BluetoothGatt.GATT_SUCCESS, extraFlags, AUTHENTICATED, true, INITIALIZING, true);
				
				start(m_initTxn);
			}
		}
	}
}
