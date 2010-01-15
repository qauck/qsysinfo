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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.ActivityManager.MemoryInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.DigitsKeyListener;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * SysInfoManager
 */
public final class SysInfoManager extends PreferenceActivity
{

	private static final String PREF_KEY_CLOG_LEVL = "clog_level"; //$NON-NLS-1$
	private static final String PREF_KEY_DLOG_LEVL = "dlog_level"; //$NON-NLS-1$
	private static final String PREF_KEY_TAG_FILTER = "tag_filter"; //$NON-NLS-1$
	private static final String PREF_KEY_PID_FILTER = "pid_filter"; //$NON-NLS-1$

	private static final String DMESG_MODE = "dmesgMode"; //$NON-NLS-1$
	private static final Pattern DMESG_TIME_PATTERN = Pattern.compile( "\\d+\\.\\d+" ); //$NON-NLS-1$

	private static final int DM_LVL_EMMERGENCY = 0;
	private static final int DM_LVL_ALERT = 1;
	private static final int DM_LVL_CRITICAL = 2;
	private static final int DM_LVL_ERROR = 3;
	private static final int DM_LVL_WARNING = 4;
	private static final int DM_LVL_NOTICE = 5;
	private static final int DM_LVL_INFORMATION = 6;
	private static final int DM_LVL_DEBUG = 7;

	private static final int DLG_ABOUT = 1;

	private static final int MSG_INIT_OK = 1;
	private static final int MSG_DISMISS_PROGRESS = 2;

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

		ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

		MemoryInfo mi = new MemoryInfo( );
		am.getMemoryInfo( mi );

		findPreference( "memory" ).setSummary( getString( R.string.storage_summary, //$NON-NLS-1$
				getTotalMemInfo( ),
				Formatter.formatFileSize( this, mi.availMem ) ) );

		String state = Environment.getExternalStorageState( );

		if ( Environment.MEDIA_MOUNTED_READ_ONLY.equals( state )
				|| Environment.MEDIA_MOUNTED.equals( state ) )
		{
			File path = Environment.getExternalStorageDirectory( );
			StatFs stat = new StatFs( path.getPath( ) );
			long blockSize = stat.getBlockSize( );
			long totalBlocks = stat.getBlockCount( );
			long availableBlocks = stat.getAvailableBlocks( );

			findPreference( "sd_storage" ).setSummary( getString( R.string.storage_summary, //$NON-NLS-1$
					Formatter.formatFileSize( this, totalBlocks * blockSize ),
					Formatter.formatFileSize( this, availableBlocks * blockSize ) ) );
		}
		else
		{
			findPreference( "sd_storage" ).setSummary( R.string.info_not_available ); //$NON-NLS-1$
		}

		File path = Environment.getDataDirectory( );
		StatFs stat = new StatFs( path.getPath( ) );
		long blockSize = stat.getBlockSize( );

		findPreference( "internal_storage" ).setSummary( getString( R.string.storage_summary, //$NON-NLS-1$
				Formatter.formatFileSize( this, stat.getBlockCount( )
						* blockSize ),
				Formatter.formatFileSize( this, stat.getAvailableBlocks( )
						* blockSize ) ) );

		try
		{
			StringBuffer sb = new StringBuffer( );

			for ( Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces( ); en.hasMoreElements( ); )
			{
				NetworkInterface intf = en.nextElement( );
				for ( Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses( ); enumIpAddr.hasMoreElements( ); )
				{
					InetAddress inetAddress = enumIpAddr.nextElement( );
					if ( !inetAddress.isLoopbackAddress( ) )
					{
						String addr = inetAddress.getHostAddress( );

						if ( !TextUtils.isEmpty( addr ) )
						{
							if ( sb.length( ) == 0 )
							{
								sb.append( addr );
							}
							else
							{
								sb.append( ", " ).append( addr ); //$NON-NLS-1$
							}
						}
					}
				}
			}

			String netAddress = sb.toString( );

			findPreference( "net_address" ).setSummary( !TextUtils.isEmpty( netAddress ) ? netAddress //$NON-NLS-1$
					: getString( R.string.info_not_available ) );
		}
		catch ( SocketException e )
		{
			Log.e( SysInfoManager.class.getName( ), e.getLocalizedMessage( ), e );
		}

	}

	private CharSequence getTotalMemInfo( )
	{
		BufferedReader reader = null;

		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( new File( "/proc/meminfo" ) ) ), //$NON-NLS-1$
					1024 );

			String line;
			String totalMsg = null;

			while ( ( line = reader.readLine( ) ) != null )
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
							Log.e( SysInfoManager.class.getName( ),
									e.getLocalizedMessage( ),
									e );
						}
					}

					return totalMsg;
				}
			}
		}
		catch ( IOException e )
		{
			Log.e( SysInfoManager.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( SysInfoManager.class.getName( ),
							ie.getLocalizedMessage( ),
							ie );
				}
			}
		}

		return getResources( ).getString( R.string.info_not_available );
	}

	private CharSequence getCpuInfo( )
	{
		BufferedReader reader = null;

		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( new File( "/proc/cpuinfo" ) ) ), //$NON-NLS-1$
					1024 );

			String line;
			String processor = null;
			String mips = null;

			while ( ( line = reader.readLine( ) ) != null )
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
			Log.e( SysInfoManager.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( SysInfoManager.class.getName( ),
							ie.getLocalizedMessage( ),
							ie );
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
		else if ( "view_logs".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					dialog.dismiss( );

					if ( which == 0 )
					{
						showLog( true );
					}
					else
					{
						showLog( false );
					}
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.view_logs )
					.setItems( new CharSequence[]{
							"Dmesg", "Logcat" //$NON-NLS-1$ //$NON-NLS-2$
					}, listener )
					.create( )
					.show( );
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

			TextView txt = new TextView( this );
			txt.setGravity( Gravity.CENTER_HORIZONTAL );
			txt.setTextAppearance( this, android.R.style.TextAppearance_Medium );
			txt.setAutoLinkMask( Linkify.EMAIL_ADDRESSES );

			txt.setText( getString( R.string.about_msg,
					getVersionName( getPackageManager( ), getPackageName( ) ) ) );

			builder.setView( txt );
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

	private void showLog( boolean dmesg )
	{
		Intent it = new Intent( Intent.ACTION_VIEW );
		it.setClass( this, LogViewer.class );
		it.putExtra( DMESG_MODE, dmesg );

		startActivityForResult( it, 1 );
	}

	private static String getVersionName( PackageManager pm, String pkgName )
	{
		String ver = null;

		try
		{
			ver = pm.getPackageInfo( pkgName, 0 ).versionName;
		}
		catch ( NameNotFoundException e )
		{
			Log.e( SysInfoManager.class.getName( ), e.getLocalizedMessage( ), e );
		}

		if ( ver == null )
		{
			ver = ""; //$NON-NLS-1$
		}

		return ver;
	}

	/**
	 * LogViewer
	 */
	public static final class LogViewer extends ListActivity
	{

		private boolean dmesgMode;
		private ProgressDialog progress;

		private Handler handler = new Handler( ) {

			public void handleMessage( android.os.Message msg )
			{
				switch ( msg.what )
				{
					case MSG_INIT_OK :

						ArrayAdapter<LogItem> adapter = (ArrayAdapter<LogItem>) getListView( ).getAdapter( );

						adapter.setNotifyOnChange( false );

						adapter.clear( );

						ArrayList<LogItem> data = (ArrayList<LogItem>) msg.obj;

						if ( data != null )
						{
							for ( LogItem log : data )
							{
								adapter.add( log );
							}
						}

						adapter.notifyDataSetChanged( );

						sendEmptyMessage( MSG_DISMISS_PROGRESS );

						if ( adapter.getCount( ) == 0 )
						{
							Toast.makeText( LogViewer.this,
									R.string.no_log_info,
									Toast.LENGTH_SHORT ).show( );
						}
						else
						{
							getListView( ).setSelection( adapter.getCount( ) - 1 );
						}

						break;
					case MSG_DISMISS_PROGRESS :

						if ( progress != null )
						{
							progress.dismiss( );
							progress = null;
						}
						break;
				}
			};
		};

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			super.onCreate( savedInstanceState );

			dmesgMode = getIntent( ).getBooleanExtra( DMESG_MODE, false );

			getListView( ).setFastScrollEnabled( true );

			registerForContextMenu( getListView( ) );

			ArrayAdapter<LogItem> adapter = new ArrayAdapter<LogItem>( LogViewer.this,
					R.layout.log_item ) {

				@Override
				public View getView( int position, View convertView,
						ViewGroup parent )
				{
					View view;
					TextView txt_head, txt_msg;

					if ( convertView == null )
					{
						view = LogViewer.this.getLayoutInflater( )
								.inflate( R.layout.log_item, parent, false );
					}
					else
					{
						view = convertView;
					}

					LogItem itm = getItem( position );

					txt_msg = (TextView) view.findViewById( R.id.txt_msg );
					txt_msg.setText( itm.getMsg( ) );

					txt_head = (TextView) view.findViewById( R.id.txt_head );

					if ( dmesgMode )
					{
						txt_head.setText( formatDLog( itm ) );

						switch ( itm.level )
						{
							case '0' :
								txt_head.setTextColor( Color.MAGENTA );
								break;
							case '1' :
								txt_head.setTextColor( Color.MAGENTA );
								break;
							case '2' :
								txt_head.setTextColor( Color.RED );
								break;
							case '3' :
								txt_head.setTextColor( Color.RED );
								break;
							case '4' :
								txt_head.setTextColor( Color.YELLOW );
								break;
							case '5' :
								txt_head.setTextColor( Color.CYAN );
								break;
							case '6' :
								txt_head.setTextColor( Color.GREEN );
								break;
							case '7' :
							default :
								txt_head.setTextColor( Color.GRAY );
								break;
						}
					}
					else
					{
						txt_head.setText( formatCLog( itm ) );

						switch ( itm.level )
						{
							case 'E' :
								txt_head.setTextColor( Color.RED );
								break;
							case 'W' :
								txt_head.setTextColor( Color.YELLOW );
								break;
							case 'I' :
								txt_head.setTextColor( Color.GREEN );
								break;
							case 'D' :
								txt_head.setTextColor( Color.CYAN );
								break;
							case 'A' :
								txt_head.setTextColor( Color.MAGENTA );
								break;
							case 'V' :
							default :
								txt_head.setTextColor( Color.GRAY );
								break;
						}
					}

					return view;
				}
			};

			getListView( ).setAdapter( adapter );

			refreshLogs( );
		}

		@Override
		public void onCreateContextMenu( ContextMenu menu, View v,
				ContextMenuInfo menuInfo )
		{
			menu.setHeaderTitle( R.string.actions );
			menu.add( R.string.copy_text );
		}

		@Override
		public boolean onContextItemSelected( MenuItem item )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;
			LogItem log = (LogItem) getListView( ).getItemAtPosition( pos );

			if ( log != null && log.getMsg( ) != null )
			{
				ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );

				if ( cm != null )
				{
					cm.setText( log.getMsg( ) );
				}
			}

			return true;
		}

		@Override
		public boolean onCreateOptionsMenu( Menu menu )
		{
			getMenuInflater( ).inflate( R.menu.log_options, menu );

			return true;
		}

		@Override
		public boolean onOptionsItemSelected( MenuItem item )
		{
			if ( item.getItemId( ) == R.id.mi_preference )
			{
				Intent it = new Intent( Intent.ACTION_VIEW );
				it.setClass( this, LogSettings.class );

				it.putExtra( DMESG_MODE, dmesgMode );

				it.putExtra( PREF_KEY_CLOG_LEVL, getCLogLevel( ) );
				it.putExtra( PREF_KEY_TAG_FILTER, getCTagFilter( ) );
				it.putExtra( PREF_KEY_PID_FILTER, getPIDFilter( ) );

				it.putExtra( PREF_KEY_DLOG_LEVL, getDLogLevel( ) );

				startActivityForResult( it, 1 );

				return true;
			}
			else if ( item.getItemId( ) == R.id.mi_plain_text )
			{
				sendLog( false, true );
				return true;
			}
			else if ( item.getItemId( ) == R.id.mi_csv )
			{
				sendLog( false, false );
				return true;
			}
			else if ( item.getItemId( ) == R.id.mi_text_compressed )
			{
				sendLog( true, true );
				return true;
			}
			else if ( item.getItemId( ) == R.id.mi_csv_compressed )
			{
				sendLog( true, false );
				return true;
			}

			return false;
		}

		private void sendLog( boolean compressed, boolean plainText )
		{
			String content = collectLogContent( compressed, plainText );

			if ( content == null )
			{
				Toast.makeText( this, R.string.no_log_info, Toast.LENGTH_SHORT )
						.show( );
			}
			else
			{
				Intent it = new Intent( Intent.ACTION_SEND );

				it.putExtra( Intent.EXTRA_SUBJECT, "Android Device Log - " //$NON-NLS-1$
						+ new Date( ).toLocaleString( ) );

				if ( compressed )
				{
					it.putExtra( Intent.EXTRA_STREAM,
							Uri.fromFile( new File( content ) ) );
					it.putExtra( Intent.EXTRA_TEXT, "Android Device Log - " //$NON-NLS-1$
							+ new Date( ).toLocaleString( ) );
					it.setType( "application/zip" ); //$NON-NLS-1$
				}
				else
				{
					it.putExtra( Intent.EXTRA_TEXT, content );
					it.setType( "text/plain" ); //$NON-NLS-1$
				}

				it = Intent.createChooser( it, null );

				startActivity( it );
			}
		}

		@Override
		protected void onActivityResult( int requestCode, int resultCode,
				Intent data )
		{
			if ( requestCode == 1 )
			{
				boolean needRefresh = false;

				int logLevel = data.getIntExtra( PREF_KEY_CLOG_LEVL,
						Log.VERBOSE );
				if ( logLevel != getCLogLevel( ) )
				{
					setCLogLevel( logLevel );
					needRefresh = true;
				}

				String tagFilter = data.getStringExtra( PREF_KEY_TAG_FILTER );
				if ( !TextUtils.equals( tagFilter, getCTagFilter( ) ) )
				{
					setCTagFilter( tagFilter );
					needRefresh = true;
				}

				int pidFilter = data.getIntExtra( PREF_KEY_PID_FILTER, 0 );
				if ( pidFilter != getPIDFilter( ) )
				{
					setPIDFilter( pidFilter );
					needRefresh = true;
				}

				logLevel = data.getIntExtra( PREF_KEY_DLOG_LEVL, DM_LVL_DEBUG );
				if ( logLevel != getDLogLevel( ) )
				{
					setDLogLevel( logLevel );
					needRefresh = true;
				}

				if ( needRefresh )
				{
					refreshLogs( );
				}
			}
		}

		private String collectLogContent( boolean compressed, boolean plainText )
		{
			String textContent = plainText ? collectTextLogContent( )
					: collectCSVLogContent( );

			if ( !compressed )
			{
				return textContent;
			}

			String state = Environment.getExternalStorageState( );

			if ( Environment.MEDIA_MOUNTED.equals( state ) )
			{
				File path = Environment.getExternalStorageDirectory( );

				File tf = new File( path, "logs" ); //$NON-NLS-1$

				if ( !tf.exists( ) )
				{
					if ( !tf.mkdirs( ) )
					{
						Toast.makeText( this,
								getString( R.string.error_create_folder,
										tf.getAbsolutePath( ) ),
								Toast.LENGTH_SHORT ).show( );

						return null;
					}
				}

				File zf = new File( tf, "android_log" //$NON-NLS-1$
						+ Math.abs( System.currentTimeMillis( ) )
						+ ".zip" ); //$NON-NLS-1$

				ZipOutputStream zos = null;
				try
				{
					zos = new ZipOutputStream( new BufferedOutputStream( new FileOutputStream( zf ) ) );

					zos.putNextEntry( new ZipEntry( plainText ? "log.txt" //$NON-NLS-1$
							: "log.csv" ) ); //$NON-NLS-1$

					zos.write( textContent.getBytes( ) );

					zos.closeEntry( );

					return zf.getAbsolutePath( );
				}
				catch ( IOException e )
				{
					Log.e( LogViewer.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
				finally
				{
					if ( zos != null )
					{
						try
						{
							zos.close( );
						}
						catch ( IOException e )
						{
							Log.e( LogViewer.class.getName( ),
									e.getLocalizedMessage( ),
									e );
						}
					}
				}
			}
			else
			{
				Toast.makeText( this, R.string.error_sdcard, Toast.LENGTH_SHORT )
						.show( );
			}

			return null;
		}

		private String collectTextLogContent( )
		{
			StringBuffer sb = new StringBuffer( );

			sb.append( getString( R.string.collector_head,
					getString( R.string.app_name ),
					getVersionName( getPackageManager( ), getPackageName( ) ) ) );

			sb.append( getString( R.string.log_head,
					Build.DEVICE,
					Build.MODEL,
					Build.PRODUCT,
					Build.BRAND,
					Build.VERSION.RELEASE,
					Build.DISPLAY ) );

			BufferedReader reader = null;
			try
			{
				reader = new BufferedReader( new InputStreamReader( new FileInputStream( "/proc/version" ) ), //$NON-NLS-1$
						1024 );

				String line;
				while ( ( line = reader.readLine( ) ) != null )
				{
					sb.append( line ).append( '\n' );
				}
			}
			catch ( IOException e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}
			finally
			{
				if ( reader != null )
				{
					try
					{
						reader.close( );
					}
					catch ( IOException e )
					{
						Log.e( LogViewer.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
			}

			sb.append( '\n' );

			ListAdapter adapter = getListView( ).getAdapter( );
			int cnt = adapter.getCount( );

			if ( dmesgMode )
			{
				String head;
				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					head = formatDLog( log ) + " :\t"; //$NON-NLS-1$

					if ( log.msgList == null )
					{
						sb.append( head ).append( log.msg ).append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( head ).append( s ).append( '\n' );
						}
					}
				}
			}
			else
			{
				String head;
				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					head = formatCLog( log ) + " :\t"; //$NON-NLS-1$

					if ( log.msgList == null )
					{
						sb.append( head ).append( log.msg ).append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( head ).append( s ).append( '\n' );
						}
					}
				}
			}

			return sb.toString( );
		}

		private String collectCSVLogContent( )
		{
			StringBuffer sb = new StringBuffer( );

			ListAdapter adapter = getListView( ).getAdapter( );
			int cnt = adapter.getCount( );

			if ( dmesgMode )
			{
				sb.append( "LEVEL,TIME,MSG\n" ); //$NON-NLS-1$

				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					if ( log.msgList == null )
					{
						sb.append( log.level ).append( ',' );

						if ( log.time != null )
						{
							sb.append( log.time.replaceAll( ",", "." ) ); //$NON-NLS-1$ //$NON-NLS-2$
						}

						sb.append( ',' )
								.append( log.msg.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
								.append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( log.level ).append( ',' );

							if ( log.time != null )
							{
								sb.append( log.time.replaceAll( ",", "." ) ); //$NON-NLS-1$ //$NON-NLS-2$
							}

							sb.append( ',' ).append( s.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
									.append( '\n' );
						}
					}
				}
			}
			else
			{
				sb.append( "TIME,LEVEL,TAG,PID,MSG\n" ); //$NON-NLS-1$

				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					if ( log.msgList == null )
					{
						sb.append( log.time.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
								.append( ',' )
								.append( log.level )
								.append( ',' )
								.append( log.tag.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
								.append( ',' )
								.append( log.pid )
								.append( ',' )
								.append( log.msg.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
								.append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( log.time.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
									.append( ',' )
									.append( log.level )
									.append( ',' )
									.append( log.tag.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
									.append( ',' )
									.append( log.pid )
									.append( ',' )
									.append( s.replaceAll( ",", "." ) ) //$NON-NLS-1$ //$NON-NLS-2$
									.append( '\n' );
						}
					}
				}
			}

			return sb.toString( );
		}

		private int getCLogLevel( )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			return sp.getInt( PREF_KEY_CLOG_LEVL, Log.VERBOSE );
		}

		private void setCLogLevel( int level )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			Editor et = sp.edit( );
			et.putInt( PREF_KEY_CLOG_LEVL, level );
			et.commit( );
		}

		private String getCTagFilter( )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			return sp.getString( PREF_KEY_TAG_FILTER, null );
		}

		private void setCTagFilter( String val )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			Editor et = sp.edit( );
			if ( val == null )
			{
				et.remove( PREF_KEY_TAG_FILTER );
			}
			else
			{
				et.putString( PREF_KEY_TAG_FILTER, val );
			}
			et.commit( );
		}

		private int getPIDFilter( )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			return sp.getInt( PREF_KEY_PID_FILTER, 0 );
		}

		private void setPIDFilter( int pid )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			Editor et = sp.edit( );
			et.putInt( PREF_KEY_PID_FILTER, pid );
			et.commit( );
		}

		private int getDLogLevel( )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			return sp.getInt( PREF_KEY_DLOG_LEVL, DM_LVL_DEBUG );
		}

		private void setDLogLevel( int level )
		{
			SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

			Editor et = sp.edit( );
			et.putInt( PREF_KEY_DLOG_LEVL, level );
			et.commit( );
		}

		private void refreshLogs( )
		{
			if ( progress == null )
			{
				progress = new ProgressDialog( this );
			}
			progress.setMessage( getResources( ).getText( R.string.loading ) );
			progress.setIndeterminate( true );
			progress.show( );

			new Thread( new Runnable( ) {

				public void run( )
				{
					ArrayList<LogItem> logs = dmesgMode ? collectDLog( )
							: collectCLog( );

					handler.sendMessage( handler.obtainMessage( MSG_INIT_OK,
							logs ) );
				}
			} ).start( );
		}

		private LogItem parseDLog( String line, char targetLevel )
		{
			int levelOffset = line.indexOf( '>' );

			if ( levelOffset < 1 )
			{
				return null;
			}

			char level = line.charAt( levelOffset - 1 );

			if ( level > targetLevel )
			{
				return null;
			}

			LogItem log = new LogItem( );
			log.level = level;

			int timeOffset = line.indexOf( ']' );

			if ( timeOffset != -1 && timeOffset > levelOffset )
			{
				String timeRaw = line.substring( levelOffset + 1, timeOffset )
						.trim( );

				if ( timeRaw.length( ) > 1 && timeRaw.charAt( 0 ) == '[' )
				{
					timeRaw = timeRaw.substring( 1 );

					if ( DMESG_TIME_PATTERN.matcher( timeRaw ).find( ) )
					{
						log.time = timeRaw;
					}
				}
			}

			log.msg = line.substring( log.time == null ? ( levelOffset + 1 )
					: ( timeOffset + 1 ) ).trim( );

			return log;
		}

		private LogItem parseCLog( String line, String tagFilter, int pidFilter )
		{
			int dayOffset = line.indexOf( ' ' );
			int timeOffset = line.indexOf( ' ', dayOffset + 1 );
			int levelOffset = line.indexOf( '/', timeOffset + 1 );
			int pidOffset = line.indexOf( "):" ); //$NON-NLS-1$
			int tagOffset = line.lastIndexOf( '(', pidOffset );

			int pid = -1;
			try
			{
				pid = Integer.parseInt( line.substring( tagOffset + 1,
						pidOffset ).trim( ) );
			}
			catch ( Exception e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}

			if ( pidFilter != 0 && pidFilter != pid )
			{
				return null;
			}

			String tag = line.substring( levelOffset + 1, tagOffset );

			if ( tagFilter != null && !tag.contains( tagFilter ) )
			{
				return null;
			}

			LogItem log = new LogItem( );
			log.tag = tag;
			log.pid = pid;
			log.time = line.substring( 0, timeOffset );
			log.level = line.charAt( levelOffset - 1 );
			log.msg = line.substring( pidOffset + 2 ).trim( );

			return log;
		}

		private String formatDLog( LogItem log )
		{
			return "<" + log.level + "> " //$NON-NLS-1$ //$NON-NLS-2$
					+ ( log.time == null ? "" : ( "[" + log.time + "] " ) ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		private String formatCLog( LogItem log )
		{
			return log.time
					+ ' '
					+ log.level
					+ '/'
					+ log.tag
					+ '('
					+ log.pid
					+ ')';
		}

		private ArrayList<LogItem> collectDLog( )
		{
			int level = getDLogLevel( );
			char dl = (char) ( level + 0x30 );

			BufferedReader reader = null;
			try
			{
				Process proc = Runtime.getRuntime( ).exec( "dmesg" ); //$NON-NLS-1$

				reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
						8192 );

				String line;
				LogItem dlog, lastDlog = null;

				ArrayList<LogItem> logs = new ArrayList<LogItem>( );

				while ( ( line = reader.readLine( ) ) != null )
				{
					dlog = parseDLog( line, dl );
					if ( dlog != null )
					{
						if ( lastDlog != null && lastDlog.mergable( dlog ) )
						{
							lastDlog.merge( dlog );
							continue;
						}

						lastDlog = dlog;
						logs.add( dlog );
					}
				}

				return logs;
			}
			catch ( Exception e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}
			finally
			{
				if ( reader != null )
				{
					try
					{
						reader.close( );
					}
					catch ( IOException e )
					{
						Log.e( LogViewer.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
			}

			return null;
		}

		private ArrayList<LogItem> collectCLog( )
		{
			int level = getCLogLevel( );
			char cl = 'V';

			switch ( level )
			{
				case Log.DEBUG :
					cl = 'D';
					break;
				case Log.INFO :
					cl = 'I';
					break;
				case Log.WARN :
					cl = 'W';
					break;
				case Log.ERROR :
					cl = 'E';
					break;
				case Log.ASSERT :
					cl = 'F';
					break;
			}

			String tagFilter = getCTagFilter( );
			int pidFilter = getPIDFilter( );

			BufferedReader reader = null;
			try
			{
				Process proc = Runtime.getRuntime( )
						.exec( "logcat -d -v time *:" + cl ); //$NON-NLS-1$

				reader = new BufferedReader( new InputStreamReader( proc.getInputStream( ) ),
						8192 * 4 );

				String line;
				LogItem clog, lastClog = null;

				ArrayList<LogItem> logs = new ArrayList<LogItem>( );

				while ( ( line = reader.readLine( ) ) != null )
				{
					clog = parseCLog( line, tagFilter, pidFilter );
					if ( clog != null )
					{
						if ( lastClog != null && lastClog.mergable( clog ) )
						{
							lastClog.merge( clog );
							continue;
						}

						lastClog = clog;
						logs.add( clog );
					}
				}

				return logs;
			}
			catch ( Exception e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}
			finally
			{
				if ( reader != null )
				{
					try
					{
						reader.close( );
					}
					catch ( IOException e )
					{
						Log.e( LogViewer.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
			}

			return null;
		}
	}

	/**
	 * LogSettings
	 */
	public static final class LogSettings extends PreferenceActivity
	{

		private boolean dmesgMode;

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			addPreferencesFromResource( R.xml.log_pref );

			dmesgMode = getIntent( ).getBooleanExtra( DMESG_MODE, false );

			if ( dmesgMode )
			{
				( (PreferenceGroup) getPreferenceScreen( ).getPreference( 0 ) ).removePreference( findPreference( "tag_filter" ) ); //$NON-NLS-1$
				( (PreferenceGroup) getPreferenceScreen( ).getPreference( 0 ) ).removePreference( findPreference( "pid_filter" ) ); //$NON-NLS-1$
			}
			else
			{
				refreshTagFilter( );
				refreshPidFilter( );
			}

			refreshLevelFilter( );

			setResult( RESULT_OK, getIntent( ) );
		}

		private void refreshLevelFilter( )
		{
			if ( dmesgMode )
			{
				int level = getIntent( ).getIntExtra( PREF_KEY_DLOG_LEVL,
						DM_LVL_DEBUG );

				CharSequence label = getString( R.string.debug );
				switch ( level )
				{
					case DM_LVL_EMMERGENCY :
						label = getString( R.string.emmergency );
						break;
					case DM_LVL_ALERT :
						label = getString( R.string.alert );
						break;
					case DM_LVL_CRITICAL :
						label = getString( R.string.critical );
						break;
					case DM_LVL_ERROR :
						label = getString( R.string.error );
						break;
					case DM_LVL_WARNING :
						label = getString( R.string.warning );
						break;
					case DM_LVL_NOTICE :
						label = getString( R.string.notice );
						break;
					case DM_LVL_INFORMATION :
						label = getString( R.string.info );
						break;
				}

				findPreference( "level_filter" ).setSummary( label ); //$NON-NLS-1$
			}
			else
			{
				int level = getIntent( ).getIntExtra( PREF_KEY_CLOG_LEVL,
						Log.VERBOSE );

				CharSequence label = getString( R.string.verbose );
				switch ( level )
				{
					case Log.DEBUG :
						label = getString( R.string.debug );
						break;
					case Log.INFO :
						label = getString( R.string.info );
						break;
					case Log.WARN :
						label = getString( R.string.warning );
						break;
					case Log.ERROR :
						label = getString( R.string.error );
						break;
					case Log.ASSERT :
						label = getString( R.string.asser_t );
						break;
				}

				findPreference( "level_filter" ).setSummary( label ); //$NON-NLS-1$
			}
		}

		private void refreshTagFilter( )
		{
			String tag = getIntent( ).getStringExtra( PREF_KEY_TAG_FILTER );

			if ( tag == null )
			{
				findPreference( "tag_filter" ).setSummary( R.string.none ); //$NON-NLS-1$
			}
			else
			{
				findPreference( "tag_filter" ).setSummary( tag ); //$NON-NLS-1$
			}
		}

		private void refreshPidFilter( )
		{
			int pid = getIntent( ).getIntExtra( PREF_KEY_PID_FILTER, 0 );

			if ( pid == 0 )
			{
				findPreference( "pid_filter" ).setSummary( R.string.none ); //$NON-NLS-1$
			}
			else
			{
				findPreference( "pid_filter" ).setSummary( String.valueOf( pid ) ); //$NON-NLS-1$
			}
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( "level_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						if ( dmesgMode )
						{
							it.putExtra( PREF_KEY_DLOG_LEVL, which );
						}
						else
						{
							it.putExtra( PREF_KEY_CLOG_LEVL, which + 2 );
						}

						dialog.dismiss( );

						refreshLevelFilter( );
					}
				};

				if ( dmesgMode )
				{
					new AlertDialog.Builder( this ).setTitle( R.string.level_filter )
							.setNeutralButton( R.string.close, null )
							.setSingleChoiceItems( new CharSequence[]{
									getString( R.string.emmergency ),
									getString( R.string.alert ),
									getString( R.string.critical ),
									getString( R.string.error ),
									getString( R.string.warning ),
									getString( R.string.notice ),
									getString( R.string.info ),
									getString( R.string.debug )
							},
									it.getIntExtra( PREF_KEY_DLOG_LEVL,
											DM_LVL_DEBUG ),
									listener )
							.create( )
							.show( );
				}
				else
				{
					new AlertDialog.Builder( this ).setTitle( R.string.level_filter )
							.setNeutralButton( R.string.close, null )
							.setSingleChoiceItems( new CharSequence[]{
									getString( R.string.verbose ),
									getString( R.string.debug ),
									getString( R.string.info ),
									getString( R.string.warning ),
									getString( R.string.error ),
									getString( R.string.asser_t )
							},
									it.getIntExtra( PREF_KEY_CLOG_LEVL,
											Log.VERBOSE ) - 2,
									listener )
							.create( )
							.show( );
				}

				return true;
			}
			else if ( "tag_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
			{
				final EditText txt = new EditText( this );
				txt.setText( it.getStringExtra( PREF_KEY_TAG_FILTER ) );

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String filter = txt.getText( ).toString( );

						if ( filter != null )
						{
							filter = filter.trim( );

							if ( filter.length( ) == 0 )
							{
								filter = null;
							}
						}

						it.putExtra( PREF_KEY_TAG_FILTER, filter );

						dialog.dismiss( );

						refreshTagFilter( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.tag_filter )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			else if ( "pid_filter".equals( preference.getKey( ) ) ) //$NON-NLS-1$
			{
				final EditText txt = new EditText( this );
				txt.setFilters( new InputFilter[]{
					DigitsKeyListener.getInstance( false, false )
				} );

				int pid = it.getIntExtra( PREF_KEY_PID_FILTER, 0 );
				if ( pid != 0 )
				{
					txt.setText( String.valueOf( pid ) );
				}

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String filter = txt.getText( ).toString( );

						if ( filter != null )
						{
							filter = filter.trim( );

							if ( filter.length( ) == 0 )
							{
								filter = null;
							}
						}

						int pid = 0;

						if ( filter != null )
						{
							try
							{
								pid = Integer.parseInt( filter );
							}
							catch ( Exception e )
							{
								Log.e( LogSettings.class.getName( ),
										e.getLocalizedMessage( ),
										e );
							}
						}

						it.putExtra( PREF_KEY_PID_FILTER, pid );

						dialog.dismiss( );

						refreshPidFilter( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.pid_filter )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			return false;
		}
	}

	/**
	 * LogItem
	 */
	private static final class LogItem
	{

		char level;
		String tag;
		int pid;
		String time;
		String msg;
		ArrayList<String> msgList;

		String getMsg( )
		{
			if ( msg == null && msgList != null )
			{
				StringBuffer sb = new StringBuffer( );

				for ( String s : msgList )
				{
					sb.append( s ).append( '\n' );
				}

				msg = sb.toString( ).trim( );
			}

			return msg;
		}

		void merge( LogItem that )
		{
			if ( msgList == null )
			{
				msgList = new ArrayList<String>( );
				msgList.add( msg );
				msg = null;
			}

			msgList.add( that.msg );
		}

		boolean mergable( LogItem that )
		{
			if ( this.level != that.level || this.pid != that.pid )
			{
				return false;
			}

			if ( !TextUtils.equals( this.tag, that.tag ) )
			{
				return false;
			}

			return TextUtils.equals( this.time, that.time );
		}
	}
}
