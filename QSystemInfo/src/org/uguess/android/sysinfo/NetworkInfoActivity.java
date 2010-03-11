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

import org.uguess.android.sysinfo.NetStateManager.IpInfo;
import org.uguess.android.sysinfo.SysInfoManager.PopActivity;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * NetworkInfoActivity
 */
public class NetworkInfoActivity extends PopActivity
{

	private ListView contentView;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		contentView = (ListView) findViewById( R.id.content_list );

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

					Button btn = (Button) cv.findViewById( R.id.btn_action );

					btn.setOnClickListener( new OnClickListener( ) {

						public void onClick( View v )
						{
							eventConsumed = true;

							IpInfo info = NetStateManager.getIpInfo( null );

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
									item[1] = info.ip + '\n' + info.host;
								}
							}
							else
							{
								item[1] = getString( R.string.info_not_available );
							}

							( (ArrayAdapter<String[]>) contentView.getAdapter( ) ).notifyDataSetChanged( );
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

		String localAddress = SysInfoManager.getNetAddressInfo( );

		data.add( new String[]{
				getString( R.string.local_address ),
				localAddress == null ? getString( R.string.unknown )
						: localAddress
		} );

		data.add( new String[]{
				getString( R.string.public_address ), null
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
