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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.uguess.android.sysinfo.SysInfoManager.PopActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * CpuInfoActivity
 */
public final class CpuInfoActivity extends PopActivity
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

				String[] item = getItem( position );

				TextView t1 = (TextView) v.findViewById( R.id.txt_head );
				TextView t2 = (TextView) v.findViewById( R.id.txt_msg );

				t1.setText( item[0] );
				t2.setText( item[1] );

				return v;
			}
		};

		contentView.setAdapter( adapter );

		refresh( );
	}

	private void refresh( )
	{
		ArrayList<String[]> data = new ArrayList<String[]>( );

		String[] stat = SysInfoManager.getCpuState( );

		if ( stat != null && stat.length == 2 )
		{
			data.add( new String[]{
					getString( R.string.model ), stat[0]
			} );

			if ( stat[1] != null )
			{
				data.add( new String[]{
						getString( R.string.cur_freq ), stat[1]
				} );
			}
		}

		String cpuMin = readFile( "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq", //$NON-NLS-1$
				true );
		String cpuMax = readFile( "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", //$NON-NLS-1$
				true );
		String scaleMin = readFile( "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq", //$NON-NLS-1$
				true );
		String scaleMax = readFile( "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq", //$NON-NLS-1$
				true );
		String governor = readFile( "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", //$NON-NLS-1$
				false );

		if ( cpuMin != null && cpuMax != null )
		{
			data.add( new String[]{
					getString( R.string.cpu_freq_range ),
					cpuMin + " - " + cpuMax //$NON-NLS-1$
			} );
		}

		if ( scaleMin != null && scaleMax != null )
		{
			data.add( new String[]{
					getString( R.string.scaling_range ),
					scaleMin + " - " + scaleMax //$NON-NLS-1$
			} );
		}

		if ( governor != null )
		{
			data.add( new String[]{
					getString( R.string.scaling_governor ), governor
			} );
		}

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

	private static String readFile( String fname, boolean freq )
	{
		File f = new File( fname );

		if ( f.exists( ) && f.isFile( ) && f.canRead( ) )
		{
			BufferedReader reader = null;
			String line;

			try
			{
				reader = new BufferedReader( new InputStreamReader( new FileInputStream( f ) ),
						32 );

				line = reader.readLine( );

				if ( line != null )
				{
					if ( freq )
					{
						return String.valueOf( Long.parseLong( line.trim( ) ) / 1000 )
								+ "MHz"; //$NON-NLS-1$
					}
					else
					{
						return line.trim( );
					}
				}
			}
			catch ( Exception e )
			{
				Log.e( CpuInfoActivity.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}
			finally
			{
				if ( reader != null )
				{
					try
					{
						reader.close( );
						reader = null;
					}
					catch ( IOException ie )
					{
						Log.e( CpuInfoActivity.class.getName( ),
								ie.getLocalizedMessage( ),
								ie );
					}
				}
			}
		}
		else
		{
			Log.d( CpuInfoActivity.class.getName( ),
					"Cannot read file: " + fname ); //$NON-NLS-1$
		}

		return null;
	}

}
