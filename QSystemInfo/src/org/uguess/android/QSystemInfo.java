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

package org.uguess.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.MessageFormat;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ActivityManager.MemoryInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * QSystemInfo
 */
public class QSystemInfo extends PreferenceActivity
{

	private static final int DLG_ABOUT = 1;

	private Preference prefBatteryLevel;

	private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver( ) {

		@Override
		public void onReceive( Context context, Intent intent )
		{
			String action = intent.getAction( );
			if ( Intent.ACTION_BATTERY_CHANGED.equals( action ) )
			{
				int level = intent.getIntExtra( "level", 0 ); //$NON-NLS-1$
				int scale = intent.getIntExtra( "scale", 100 ); //$NON-NLS-1$

				prefBatteryLevel.setSummary( String.valueOf( level
						* 100
						/ scale )
						+ "%" ); //$NON-NLS-1$
			}
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		addPreferencesFromResource( R.xml.main );

		prefBatteryLevel = findPreference( "battery_level" ); //$NON-NLS-1$
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		registerReceiver( mBatteryInfoReceiver,
				new IntentFilter( Intent.ACTION_BATTERY_CHANGED ) );

		updateInfo( );
	}

	@Override
	protected void onPause( )
	{
		super.onPause( );

		unregisterReceiver( mBatteryInfoReceiver );
	}

	private void updateInfo( )
	{
		findPreference( "processor" ).setSummary( getCpuInfo( ) ); //$NON-NLS-1$
		findPreference( "total_memory" ).setSummary( getTotalMemInfo( ) ); //$NON-NLS-1$

		ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

		MemoryInfo mi = new MemoryInfo( );
		am.getMemoryInfo( mi );

		findPreference( "available_memory" ).setSummary( Formatter.formatFileSize( this, //$NON-NLS-1$
				mi.availMem ) );

		String state = Environment.getExternalStorageState( );

		if ( Environment.MEDIA_MOUNTED_READ_ONLY.equals( state )
				|| Environment.MEDIA_MOUNTED.equals( state ) )
		{
			File path = Environment.getExternalStorageDirectory( );
			StatFs stat = new StatFs( path.getPath( ) );
			long blockSize = stat.getBlockSize( );
			long totalBlocks = stat.getBlockCount( );
			long availableBlocks = stat.getAvailableBlocks( );

			findPreference( "total_sd_storage" ).setSummary( Formatter.formatFileSize( this, //$NON-NLS-1$
					totalBlocks * blockSize ) );

			findPreference( "available_sd_storage" ).setSummary( Formatter.formatFileSize( this, //$NON-NLS-1$
					availableBlocks * blockSize ) );
		}
		else
		{
			findPreference( "total_sd_storage" ).setSummary( R.string.info_not_available ); //$NON-NLS-1$
			findPreference( "available_sd_storage" ).setSummary( R.string.info_not_available ); //$NON-NLS-1$
		}

		File path = Environment.getDataDirectory( );
		StatFs stat = new StatFs( path.getPath( ) );
		long blockSize = stat.getBlockSize( );
		long availableBlocks = stat.getAvailableBlocks( );

		findPreference( "available_internal_storage" ).setSummary( Formatter.formatFileSize( this, //$NON-NLS-1$
				availableBlocks * blockSize ) );
	}

	private CharSequence getTotalMemInfo( )
	{
		FileInputStream fi = null;

		try
		{
			fi = new FileInputStream( new File( "/proc/meminfo" ) ); //$NON-NLS-1$

			InputStreamReader reader = new InputStreamReader( fi );

			LineNumberReader lre = new LineNumberReader( reader );

			String line;
			String totalMsg = null;

			while ( ( line = lre.readLine( ) ) != null )
			{
				if ( line.startsWith( "MemTotal" ) ) //$NON-NLS-1$
				{
					totalMsg = line;
					break;
				}
			}

			if ( totalMsg != null )
			{
				int idx = totalMsg.indexOf( ':' );

				if ( idx != -1 )
				{
					totalMsg = totalMsg.substring( idx + 1 ).trim( );

					idx = totalMsg.lastIndexOf( ' ' );

					if ( idx != -1 )
					{
						String unit = totalMsg.substring( idx + 1 );

						try
						{
							long size = Long.parseLong( totalMsg.substring( 0,
									idx ).trim( ) );

							if ( "kb".equalsIgnoreCase( unit ) ) //$NON-NLS-1$
							{
								size *= 1024;
							}
							else if ( "mb".equalsIgnoreCase( unit ) ) //$NON-NLS-1$
							{
								size *= 1024 * 1024;
							}
							else if ( "gb".equalsIgnoreCase( unit ) ) //$NON-NLS-1$
							{
								size *= 1024 * 1024 * 1024;
							}

							totalMsg = Formatter.formatFileSize( this, size );
						}
						catch ( Exception e )
						{
						}
					}

					return totalMsg;
				}
			}
		}
		catch ( IOException e )
		{
		}
		finally
		{
			if ( fi != null )
			{
				try
				{
					fi.close( );
				}
				catch ( IOException ie )
				{
				}
			}
		}

		return getResources( ).getString( R.string.info_not_available );
	}

	private CharSequence getCpuInfo( )
	{
		FileInputStream fi = null;

		try
		{
			fi = new FileInputStream( new File( "/proc/cpuinfo" ) ); //$NON-NLS-1$

			InputStreamReader reader = new InputStreamReader( fi );

			LineNumberReader lre = new LineNumberReader( reader );

			String line;
			String processor = null;
			String mips = null;

			while ( ( line = lre.readLine( ) ) != null )
			{
				if ( line.startsWith( "Processor" ) ) //$NON-NLS-1$
				{
					processor = line;
				}
				else if ( line.startsWith( "BogoMIPS" ) ) //$NON-NLS-1$
				{
					mips = line;
				}

				if ( processor != null && mips != null )
				{
					break;
				}
			}

			if ( processor != null && mips != null )
			{
				int idx = processor.indexOf( ':' );
				if ( idx != -1 )
				{
					processor = processor.substring( idx + 1 ).trim( );

					idx = mips.indexOf( ':' );
					if ( idx != -1 )
					{
						mips = mips.substring( idx + 1 ).trim( );

						return processor + "  " + mips + "MHz"; //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		catch ( IOException e )
		{
		}
		finally
		{
			if ( fi != null )
			{
				try
				{
					fi.close( );
				}
				catch ( IOException ie )
				{
				}
			}
		}

		return getResources( ).getString( R.string.info_not_available );

	}

	@Override
	public boolean onPreferenceTreeClick( PreferenceScreen preferenceScreen,
			Preference preference )
	{
		if ( "refresh_status".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			updateInfo( );
			return true;
		}
		else if ( "manage_apps".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent intent = new Intent( Intent.ACTION_VIEW );
			intent.setClass( this, ApplicationManager.class );
			startActivity( intent );
			return true;
		}
		else if ( "more_info".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent intent = new Intent( Intent.ACTION_VIEW );
			intent.setClassName( "com.android.settings", //$NON-NLS-1$
					"com.android.settings.DeviceInfoSettings" ); //$NON-NLS-1$
			startActivity( intent );
			return true;
		}

		return false;
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		if ( id == DLG_ABOUT )
		{
			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setTitle( R.string.app_name );
			builder.setIcon( R.drawable.icon );

			String ver = null;

			try
			{
				ver = getPackageManager( ).getPackageInfo( getPackageName( ), 0 ).versionName;
			}
			catch ( NameNotFoundException e )
			{
				Log.e( QSystemInfo.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			if ( ver == null )
			{
				ver = ""; //$NON-NLS-1$
			}

			builder.setMessage( MessageFormat.format( getResources( ).getString( R.string.about_msg ),
					ver ) );

			builder.setNegativeButton( R.string.close, null );

			return builder.create( );
		}
		return super.onCreateDialog( id );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuInflater inflater = getMenuInflater( );
		inflater.inflate( R.menu.main_options, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_about )
		{
			showDialog( DLG_ABOUT );
			return true;
		}

		return false;
	}

}