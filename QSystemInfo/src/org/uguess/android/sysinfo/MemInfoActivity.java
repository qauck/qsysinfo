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

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * MemInfoActivity
 */
public final class MemInfoActivity extends PopActivity
{

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		final ListView contentView = (ListView) findViewById( R.id.content_list );

		registerForContextMenu( contentView );

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

		refresh( );
	}

	private void refresh( )
	{
		ArrayList<String[]> data = collectMemInfo( this );

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

	private static ArrayList<String[]> collectMemInfo( Context ctx )
	{
		ArrayList<String[]> data = new ArrayList<String[]>( );

		BufferedReader reader = null;

		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( new File( "/proc/meminfo" ) ) ), //$NON-NLS-1$
					1024 );

			String line;
			String totalMsg = null;
			String freeMsg = null;
			String buffersMsg = null;
			String cachedMsg = null;
			String swapTotalMsg = null;
			String swapFreeMsg = null;

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( totalMsg == null && line.startsWith( "MemTotal" ) ) //$NON-NLS-1$
				{
					totalMsg = line;
				}
				else if ( freeMsg == null && line.startsWith( "MemFree" ) ) //$NON-NLS-1$
				{
					freeMsg = line;
				}
				else if ( buffersMsg == null && line.startsWith( "Buffers" ) ) //$NON-NLS-1$
				{
					buffersMsg = line;
				}
				else if ( cachedMsg == null && line.startsWith( "Cached" ) ) //$NON-NLS-1$
				{
					cachedMsg = line;
				}
				else if ( swapTotalMsg == null && line.startsWith( "SwapTotal" ) ) //$NON-NLS-1$
				{
					swapTotalMsg = line;
				}
				else if ( swapFreeMsg == null && line.startsWith( "SwapFree" ) ) //$NON-NLS-1$
				{
					swapFreeMsg = line;
				}

				if ( totalMsg != null
						&& freeMsg != null
						&& buffersMsg != null
						&& cachedMsg != null
						&& swapTotalMsg != null
						&& swapFreeMsg != null )
				{
					break;
				}
			}

			long total = SysInfoManager.extractMemCount( totalMsg );
			long free = SysInfoManager.extractMemCount( freeMsg );
			long buffers = SysInfoManager.extractMemCount( buffersMsg );
			long cached = SysInfoManager.extractMemCount( cachedMsg );
			long swapTotal = SysInfoManager.extractMemCount( swapTotalMsg );
			long swapFree = SysInfoManager.extractMemCount( swapFreeMsg );

			ActivityManager am = (ActivityManager) ctx.getSystemService( Context.ACTIVITY_SERVICE );
			MemoryInfo mi = new MemoryInfo( );
			am.getMemoryInfo( mi );

			data.add( new String[]{
					ctx.getString( R.string.total ), formatSize( total, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.free ) + ":", //$NON-NLS-1$
					formatSize( mi.availMem, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.idle ), formatSize( free, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.threshold ),
					formatSize( mi.threshold, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.buffers ),
					formatSize( buffers, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.cached ), formatSize( cached, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.swap_total ),
					formatSize( swapTotal, ctx )
			} );
			data.add( new String[]{
					ctx.getString( R.string.swap_free ),
					formatSize( swapFree, ctx )
			} );
		}
		catch ( Exception e )
		{
			Log.e( MemInfoActivity.class.getName( ), e.getLocalizedMessage( ), e );
		}
		finally
		{
			if ( reader != null )
			{
				try
				{
					reader.close( );
				}
				catch ( IOException ie )
				{
					Log.e( MemInfoActivity.class.getName( ),
							ie.getLocalizedMessage( ),
							ie );
				}
			}
		}

		return data;
	}

	private static String formatSize( long size, Context ctx )
	{
		if ( size == -1 )
		{
			return ctx.getString( R.string.info_not_available );
		}
		else
		{
			return Formatter.formatFileSize( ctx, size );
		}
	}
}
