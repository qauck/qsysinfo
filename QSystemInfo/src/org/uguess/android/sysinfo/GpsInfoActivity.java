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

import org.uguess.android.sysinfo.SysInfoManager.PopActivity;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.Listener;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * GpsInfoActivity
 */
public final class GpsInfoActivity extends PopActivity implements
		Listener,
		LocationListener
{

	private ListView contentView;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		contentView = (ListView) findViewById( R.id.content_list );

		ArrayAdapter<GpsSatellite> adapter = new ArrayAdapter<GpsSatellite>( this,
				R.layout.sensor_item ) {

			@Override
			public View getView( int position, View convertView,
					ViewGroup parent )
			{
				View v;

				if ( convertView == null )
				{
					v = getLayoutInflater( ).inflate( R.layout.sensor_item,
							contentView,
							false );
				}
				else
				{
					v = convertView;
				}

				GpsSatellite item = getItem( position );

				TextView t1 = (TextView) v.findViewById( R.id.txt_head );
				TextView t2 = (TextView) v.findViewById( R.id.txt_msg );

				t1.setText( "" + item.getPrn( ) + " (" + item.getSnr( ) + ")" );
				t2.setText( ""
						+ item.getAzimuth( )
						+ ", "
						+ item.getElevation( ) );

				return v;
			}
		};

		contentView.setAdapter( adapter );
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		LocationManager lm = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

		if ( lm != null )
		{
			lm.addGpsStatusListener( this );

			lm.requestLocationUpdates( LocationManager.GPS_PROVIDER,
					1000,
					1,
					this );
		}
	}

	@Override
	protected void onPause( )
	{
		LocationManager lm = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

		if ( lm != null )
		{
			lm.removeGpsStatusListener( this );

			lm.removeUpdates( this );
		}

		super.onPause( );
	}

	private void refresh( )
	{
		LocationManager lm = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

		if ( lm != null )
		{
			GpsStatus gs = lm.getGpsStatus( null );

			ArrayAdapter<GpsSatellite> adapter = (ArrayAdapter<GpsSatellite>) contentView.getAdapter( );

			adapter.setNotifyOnChange( false );

			adapter.clear( );

			for ( GpsSatellite gt : gs.getSatellites( ) )
			{
				adapter.add( gt );
			}

			adapter.notifyDataSetChanged( );
		}
	}

	public void onGpsStatusChanged( int event )
	{
		refresh( );
	}

	public void onLocationChanged( Location location )
	{
		String text = location.toString( );
	}

	public void onProviderDisabled( String provider )
	{
		// TODO Auto-generated method stub

	}

	public void onProviderEnabled( String provider )
	{
		// TODO Auto-generated method stub

	}

	public void onStatusChanged( String provider, int status, Bundle extras )
	{
		// refresh( );

	}
}
