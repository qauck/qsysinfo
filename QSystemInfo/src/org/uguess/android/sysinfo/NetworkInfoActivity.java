/********************************************************************************
 * (C) Copyright 2000-2010, by Shawn Qualia.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ********************************************************************************/

package org.uguess.android.sysinfo;

import java.util.ArrayList;

import org.uguess.android.sysinfo.NetStateManager.IpInfo;
import org.uguess.android.sysinfo.SysInfoManager.PopActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * NetworkInfoActivity
 */
public final class NetworkInfoActivity extends PopActivity
{

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		final ListView contentView = (ListView) findViewById( R.id.content_list );

		registerForContextMenu( contentView );

		ArrayAdapter<String[]> adapter = new ArrayAdapter<String[]>( this,
				R.layout.sensor_item ) {

			@Override
			public View getView( int position, View convertView,
					ViewGroup parent )
			{
				View v = getLayoutInflater( ).inflate( R.layout.sensor_item,
						contentView,
						false );

				final String[] item = getItem( position );

				TextView t1 = (TextView) v.findViewById( R.id.txt_head );
				TextView t2 = (TextView) v.findViewById( R.id.txt_msg );

				t1.setText( item[0] );
				t2.setText( item[1] );

				// update last public address section
				if ( position == getCount( ) - 1 && item[1] == null )
				{
					t2.setVisibility( View.GONE );

					LinearLayout cv = (LinearLayout) getLayoutInflater( ).inflate( R.layout.pub_info,
							(LinearLayout) v,
							false );
					cv.setPadding( 0, 0, 0, 0 );

					Button btn = (Button) cv.findViewById( R.id.btn_action );

					btn.setOnClickListener( new OnClickListener( ) {

						public void onClick( View v )
						{
							eventConsumed = true;

							final ProgressDialog progress = new ProgressDialog( NetworkInfoActivity.this );
							progress.setMessage( getString( R.string.query_ip_msg ) );
							progress.setIndeterminate( true );
							progress.show( );

							new Thread( new Runnable( ) {

								public void run( )
								{
									final IpInfo info = NetStateManager.getIpInfo( null );

									contentView.post( new Runnable( ) {

										public void run( )
										{
											progress.dismiss( );

											NetStateManager.showIpInfo( info,
													NetworkInfoActivity.this );

											if ( info != null
													&& !TextUtils.isEmpty( info.latitude )
													&& !TextUtils.isEmpty( info.longitude ) )
											{
												if ( info.host == null )
												{
													item[1] = info.ip;
												}
												else
												{
													item[1] = info.ip
															+ '\n'
															+ info.host;
												}
											}
											else
											{
												item[1] = getString( R.string.info_not_available );
											}

											( (ArrayAdapter<String[]>) contentView.getAdapter( ) ).notifyDataSetChanged( );
										}
									} );
								}
							},
									"IpInfoRequester" ).start( ); //$NON-NLS-1$
						}
					} );

					( (LinearLayout) v ).addView( cv );
				}

				return v;
			}
		};

		contentView.setAdapter( adapter );

		refresh( );
	}

	private void refresh( )
	{
		ArrayList<String[]> data = new ArrayList<String[]>( );

		String type = getString( R.string.unknown );
		String subType = null;
		String extra = null;
		boolean roaming = false;

		ConnectivityManager cm = (ConnectivityManager) getSystemService( Activity.CONNECTIVITY_SERVICE );

		NetworkInfo ni = cm.getActiveNetworkInfo( );

		if ( ni != null && ni.isConnected( ) )
		{
			type = ni.getTypeName( );
			subType = ni.getSubtypeName( );
			extra = ni.getExtraInfo( );
			roaming = ni.isRoaming( );
		}

		data.add( new String[]{
				getString( R.string.network_type ),
				TextUtils.isEmpty( subType ) ? type : type + " (" //$NON-NLS-1$
						+ subType
						+ ')'
		} );

		if ( !TextUtils.isEmpty( extra ) )
		{
			data.add( new String[]{
					getString( R.string.extra_info ), extra
			} );
		}

		data.add( new String[]{
				getString( R.string.roaming_state ),
				getString( roaming ? R.string.roaming : R.string.not_roaming )
		} );

		if ( ni != null
				&& ni.isConnected( )
				&& ni.getType( ) == ConnectivityManager.TYPE_WIFI )
		{
			StringBuilder sb = new StringBuilder( );

			WifiManager wm = (WifiManager) getSystemService( Context.WIFI_SERVICE );

			WifiInfo wi = wm.getConnectionInfo( );

			if ( wi != null )
			{
				sb.append( "SSID: " ).append( wi.getSSID( ) ).append( '\n' ); //$NON-NLS-1$
				sb.append( "BSSID: " ).append( wi.getBSSID( ) ).append( '\n' ); //$NON-NLS-1$
				sb.append( getString( R.string.mac_addr ) ).append( ": " ) //$NON-NLS-1$
						.append( wi.getMacAddress( ) )
						.append( '\n' );
				sb.append( getString( R.string.link_speed ) ).append( ": " ) //$NON-NLS-1$
						.append( wi.getLinkSpeed( ) )
						.append( WifiInfo.LINK_SPEED_UNITS )
						.append( '\n' );
				sb.append( getString( R.string.sig_strength ) ).append( ": " ) //$NON-NLS-1$
						.append( wi.getRssi( ) )
						.append( '\n' );
			}

			DhcpInfo di = wm.getDhcpInfo( );

			if ( di != null )
			{
				sb.append( getString( R.string.dhcp_srv ) );
				putAddress( sb, di.serverAddress );
				sb.append( getString( R.string.gateway ) );
				putAddress( sb, di.gateway );
				sb.append( getString( R.string.ip_addr ) );
				putAddress( sb, di.ipAddress );
				sb.append( getString( R.string.netmask ) );
				putAddress( sb, di.netmask );
				sb.append( "DNS 1" ); //$NON-NLS-1$
				putAddress( sb, di.dns1 );
				sb.append( "DNS 2" ); //$NON-NLS-1$
				putAddress( sb, di.dns2 );
				sb.append( getString( R.string.lease_duration ) ).append( ": " ) //$NON-NLS-1$
						.append( di.leaseDuration )
						.append( ' ' )
						.append( getString( R.string.seconds ) );
			}

			data.add( new String[]{
					getString( R.string.wifi_state ),
					sb.length( ) == 0 ? getString( R.string.unknown )
							: sb.toString( )
			} );
		}

		String localAddress = SysInfoManager.getNetAddressInfo( );

		data.add( new String[]{
				getString( R.string.local_address ),
				localAddress == null ? getString( R.string.unknown )
						: localAddress
		} );

		data.add( new String[]{
				getString( R.string.public_address ), null
		} );

		ListView contentView = (ListView) findViewById( R.id.content_list );

		ArrayAdapter<String[]> adapter = (ArrayAdapter<String[]>) contentView.getAdapter( );

		adapter.setNotifyOnChange( false );

		adapter.clear( );

		for ( String[] d : data )
		{
			adapter.add( d );
		}

		adapter.notifyDataSetChanged( );
	}

	private void putAddress( StringBuilder buf, int addr )
	{
		buf.append( ": " ) //$NON-NLS-1$
				.append( addr & 0xff )
				.append( '.' )
				.append( ( addr >>>= 8 ) & 0xff )
				.append( '.' )
				.append( ( addr >>>= 8 ) & 0xff )
				.append( '.' )
				.append( ( addr >>>= 8 ) & 0xff )
				.append( '\n' );
	}
}
