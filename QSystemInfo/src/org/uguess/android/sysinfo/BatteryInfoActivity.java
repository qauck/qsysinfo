/********************************************************************************
 * (C) Copyright 2000-2009, by Shawn Qualia.
 *
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by 
 * the Free Software Foundation; either version 2.1 of the License, or 
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 ********************************************************************************/

package org.uguess.android.sysinfo;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * BatteryInfoActivity
 */
public final class BatteryInfoActivity extends SysInfoManager.PopActivity
{

	private ListView contentView;

	private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver( ) {

		@Override
		public void onReceive( Context context, Intent intent )
		{
			String action = intent.getAction( );
			if ( Intent.ACTION_BATTERY_CHANGED.equals( action ) )
			{
				ArrayList<String[]> data = new ArrayList<String[]>( );

				int level = intent.getIntExtra( "level", 0 ); //$NON-NLS-1$
				int scale = intent.getIntExtra( "scale", 100 ); //$NON-NLS-1$

				String lStr = String.valueOf( level * 100 / scale ) + '%';

				data.add( new String[]{
						getString( R.string.batt_level ), lStr
				} );

				int health = intent.getIntExtra( "health", //$NON-NLS-1$
						BatteryManager.BATTERY_HEALTH_UNKNOWN );

				String hStr = context.getString( R.string.unknown );

				switch ( health )
				{
					case BatteryManager.BATTERY_HEALTH_GOOD :
						hStr = getString( R.string.good );
						break;
					case BatteryManager.BATTERY_HEALTH_OVERHEAT :
						hStr = getString( R.string.over_heat );
						break;
					case BatteryManager.BATTERY_HEALTH_DEAD :
						hStr = getString( R.string.dead );
						break;
					case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE :
						hStr = getString( R.string.over_voltage );
						break;
					case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE :
						hStr = getString( R.string.failure );
						break;
				}

				data.add( new String[]{
						getString( R.string.batt_health ), hStr
				} );

				int status = intent.getIntExtra( "status", //$NON-NLS-1$
						BatteryManager.BATTERY_STATUS_UNKNOWN );

				String sStr = context.getString( R.string.unknown );

				switch ( status )
				{
					case BatteryManager.BATTERY_STATUS_CHARGING :
						sStr = getString( R.string.charging );
						break;
					case BatteryManager.BATTERY_STATUS_DISCHARGING :
						sStr = getString( R.string.discharging );
						break;
					case BatteryManager.BATTERY_STATUS_FULL :
						sStr = getString( R.string.full );
						break;
					case BatteryManager.BATTERY_STATUS_NOT_CHARGING :
						sStr = getString( R.string.not_charging );
						break;
				}

				data.add( new String[]{
						getString( R.string.batt_status ), sStr
				} );

				String tStr = intent.getStringExtra( "technology" ); //$NON-NLS-1$

				data.add( new String[]{
						getString( R.string.batt_tech ), tStr
				} );

				int voltage = intent.getIntExtra( "voltage", 0 ); //$NON-NLS-1$

				String vStr = String.valueOf( voltage ) + "mV"; //$NON-NLS-1$

				data.add( new String[]{
						getString( R.string.batt_voltage ), vStr
				} );

				int temperature = intent.getIntExtra( "temperature", 0 ); //$NON-NLS-1$

				int tens = temperature / 10;

				String tpStr = String.valueOf( Integer.toString( tens ) + "." //$NON-NLS-1$
						+ ( temperature - 10 * tens ) ) + "\u00B0 C"; //$NON-NLS-1$

				data.add( new String[]{
						getString( R.string.batt_temp ), tpStr
				} );

				int plugged = intent.getIntExtra( "plugged", 0 ); //$NON-NLS-1$

				String pStr = context.getString( R.string.unknown );

				switch ( plugged )
				{
					case 0 :
						pStr = getString( R.string.unplugged );
						break;
					case BatteryManager.BATTERY_PLUGGED_AC :
						pStr = "AC"; //$NON-NLS-1$
						break;
					case BatteryManager.BATTERY_PLUGGED_USB :
						pStr = "USB"; //$NON-NLS-1$
						break;
					case BatteryManager.BATTERY_PLUGGED_AC
							| BatteryManager.BATTERY_PLUGGED_USB :
						pStr = "AC USB"; //$NON-NLS-1$
						break;
				}

				data.add( new String[]{
						getString( R.string.batt_plugged ), pStr
				} );

				ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter( );

				adapter.setNotifyOnChange( false );

				adapter.clear( );

				for ( String[] d : data )
				{
					adapter.add( d );
				}

				adapter.notifyDataSetChanged( );
			}
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		contentView = (ListView) findViewById( R.id.content_list );

		ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>( this,
				R.layout.battery_item ) {

			@Override
			public View getView( int position, View convertView,
					ViewGroup parent )
			{
				View v;

				if ( convertView == null )
				{
					v = getLayoutInflater( ).inflate( R.layout.battery_item,
							contentView,
							false );
				}
				else
				{
					v = convertView;
				}

				String[] item = getItem( position );

				TextView t1 = (TextView) v.findViewById( R.id.txt_head );
				TextView t2 = (TextView) v.findViewById( R.id.txt_msg );

				t1.setText( item[0] );
				t2.setText( item[1] );

				return v;
			}
		};

		contentView.setAdapter( adapter );
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		registerReceiver( mBatteryInfoReceiver,
				new IntentFilter( Intent.ACTION_BATTERY_CHANGED ) );
	}

	@Override
	protected void onPause( )
	{
		unregisterReceiver( mBatteryInfoReceiver );

		super.onPause( );
	}
}
