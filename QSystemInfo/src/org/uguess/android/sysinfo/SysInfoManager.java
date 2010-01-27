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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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
import android.text.Html;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.DigitsKeyListener;
import android.text.method.LinkMovementMethod;
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

	private static final char[] CSV_SEARCH_CHARS = new char[]{
			',', '"', '\r', '\n'
	};
	private static final char[] HTML_SEARCH_CHARS = new char[]{
			'<', '>', '&', '\'', '"', '\n'
	};

	private static final String F_SCALE_FREQ = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"; //$NON-NLS-1$
	private static final String F_MEM_INFO = "/proc/meminfo"; //$NON-NLS-1$
	private static final String F_CPU_INFO = "/proc/cpuinfo"; //$NON-NLS-1$
	private static final String F_VERSION = "/proc/version"; //$NON-NLS-1$

	private static final String HEADER_SPLIT = "========================================================================================\n"; //$NON-NLS-1$

	private static final String openHeaderRow = "<tr align=\"left\" bgcolor=\"#E0E0FF\"><td><b>"; //$NON-NLS-1$
	private static final String closeHeaderRow = "</b></td><td colspan=4/></tr>\n"; //$NON-NLS-1$
	private static final String openRow = "<tr align=\"left\" valign=\"top\"><td nowrap><small>"; //$NON-NLS-1$
	private static final String openTitleRow = "<tr bgcolor=\"#E0E0E0\" align=\"left\" valign=\"top\"><td><small>"; //$NON-NLS-1$
	private static final String openFullRow = "<tr align=\"left\" valign=\"top\"><td colspan=5><small>"; //$NON-NLS-1$
	private static final String closeRow = "</small></td></tr>\n"; //$NON-NLS-1$
	private static final String nextColumn = "</small></td><td><small>"; //$NON-NLS-1$
	private static final String nextColumn4 = "</small></td><td colspan=4><small>"; //$NON-NLS-1$
	private static final String emptyRow = "<tr><td>&nbsp;</td></tr>\n"; //$NON-NLS-1$

	private static final int DM_LVL_EMMERGENCY = 0;
	private static final int DM_LVL_ALERT = 1;
	private static final int DM_LVL_CRITICAL = 2;
	private static final int DM_LVL_ERROR = 3;
	private static final int DM_LVL_WARNING = 4;
	private static final int DM_LVL_NOTICE = 5;
	private static final int DM_LVL_INFORMATION = 6;
	private static final int DM_LVL_DEBUG = 7;

	private static final int MSG_INIT_OK = 1;
	private static final int MSG_DISMISS_PROGRESS = 2;
	private static final int MSG_CONTENT_READY = 3;
	private static final int MSG_CHECK_FORCE_COMPRESSION = 4;

	private static final int PLAINTEXT = 0;
	private static final int HTML = 1;
	private static final int CSV = 2;

	private static final int BASIC_INFO = 0;
	private static final int APPLICATIONS = 1;
	private static final int PROCESSES = 2;
	private static final int NETSTATES = 3;
	private static final int DMESG_LOG = 4;
	private static final int LOGCAT_LOG = 5;

	private Preference prefBatteryLevel;
	private ProgressDialog progress;

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

	private Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_CONTENT_READY :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					String content = (String) msg.obj;

					if ( content == null )
					{
						Toast.makeText( SysInfoManager.this,
								R.string.no_report,
								Toast.LENGTH_SHORT ).show( );
					}
					else
					{
						sendContent( SysInfoManager.this,
								"Android System Report - " + new Date( ).toLocaleString( ), //$NON-NLS-1$
								content,
								msg.arg2 == 1 );
					}

					break;
				case MSG_CHECK_FORCE_COMPRESSION :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					checkForceCompression( this,
							SysInfoManager.this,
							(String) msg.obj,
							msg.arg1,
							"android_report" ); //$NON-NLS-1$

					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
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

		String[] mi = getMemInfo( );
		findPreference( "memory" ).setSummary( mi == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: ( getString( R.string.storage_summary, mi[0], mi[2] ) + getString( R.string.idle_info,
						mi[1] ) ) );

		String[] ei = getExternalStorageInfo( );
		findPreference( "sd_storage" ).setSummary( ei == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, ei[0], ei[1] ) );

		String[] ii = getInternalStorageInfo( );
		findPreference( "internal_storage" ).setSummary( getString( R.string.storage_summary, //$NON-NLS-1$
				ii[0],
				ii[1] ) );

		findPreference( "net_address" ).setSummary( getNetAddressInfo( ) ); //$NON-NLS-1$
	}

	private String[] getMemInfo( )
	{
		BufferedReader reader = null;

		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( new File( F_MEM_INFO ) ) ),
					1024 );

			String line;
			String totalMsg = null;
			String freeMsg = null;

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( line.startsWith( "MemTotal" ) ) //$NON-NLS-1$
				{
					totalMsg = line;
				}
				else if ( line.startsWith( "MemFree" ) ) //$NON-NLS-1$
				{
					freeMsg = line;
				}

				if ( totalMsg != null && freeMsg != null )
				{
					break;
				}
			}

			String[] mem = new String[3];

			mem[0] = extractMemCount( totalMsg );
			mem[1] = extractMemCount( freeMsg );

			ActivityManager am = (ActivityManager) getSystemService( Context.ACTIVITY_SERVICE );
			MemoryInfo mi = new MemoryInfo( );
			am.getMemoryInfo( mi );
			mem[2] = Formatter.formatFileSize( this, mi.availMem );

			return mem;
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

		return null;
	}

	private String extractMemCount( String line )
	{
		if ( line != null )
		{
			int idx = line.indexOf( ':' );

			if ( idx != -1 )
			{
				line = line.substring( idx + 1 ).trim( );

				idx = line.lastIndexOf( ' ' );

				if ( idx != -1 )
				{
					String unit = line.substring( idx + 1 );

					try
					{
						long size = Long.parseLong( line.substring( 0, idx )
								.trim( ) );

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
						else
						{
							Log.w( SysInfoManager.class.getName( ),
									"Unexpected mem unit format: " + line ); //$NON-NLS-1$
						}

						return Formatter.formatFileSize( this, size );
					}
					catch ( Exception e )
					{
						Log.e( SysInfoManager.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
				else
				{
					Log.e( SysInfoManager.class.getName( ),
							"Unexpected mem value format: " + line ); //$NON-NLS-1$
				}
			}
			else
			{
				Log.e( SysInfoManager.class.getName( ),
						"Unexpected mem format: " + line ); //$NON-NLS-1$
			}
		}

		return getResources( ).getString( R.string.info_not_available );
	}

	private String getCpuInfo( )
	{
		BufferedReader reader = null;

		try
		{
			String line;
			String processor = null;
			String mips = null;

			File f = new File( F_SCALE_FREQ );

			if ( f.exists( ) )
			{
				try
				{
					reader = new BufferedReader( new InputStreamReader( new FileInputStream( f ) ),
							32 );

					line = reader.readLine( );

					if ( line != null )
					{
						long freq = Long.parseLong( line.trim( ) );

						mips = String.valueOf( freq / 1000f );
					}
				}
				catch ( Exception e )
				{
					Log.e( SysInfoManager.class.getName( ),
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
							Log.e( SysInfoManager.class.getName( ),
									ie.getLocalizedMessage( ),
									ie );
						}
					}
				}
			}
			else
			{
				Log.d( SysInfoManager.class.getName( ),
						"No scaling found, using BogoMips instead" ); //$NON-NLS-1$
			}

			reader = new BufferedReader( new InputStreamReader( new FileInputStream( new File( F_CPU_INFO ) ) ),
					1024 );

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( line.startsWith( "Processor" ) ) //$NON-NLS-1$
				{
					processor = line;
				}
				else if ( mips == null && line.startsWith( "BogoMIPS" ) ) //$NON-NLS-1$
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
					}

					return processor + "  " + mips + "MHz"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				else
				{
					Log.e( SysInfoManager.class.getName( ),
							"Unexpected processor format: " + processor ); //$NON-NLS-1$
				}
			}
			else
			{
				Log.e( SysInfoManager.class.getName( ),
						"Incompatible cpu format" ); //$NON-NLS-1$
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

	private String[] getExternalStorageInfo( )
	{
		String state = Environment.getExternalStorageState( );

		if ( Environment.MEDIA_MOUNTED_READ_ONLY.equals( state )
				|| Environment.MEDIA_MOUNTED.equals( state ) )
		{
			return getStorageInfo( Environment.getExternalStorageDirectory( ) );
		}

		return null;
	}

	private String[] getInternalStorageInfo( )
	{
		return getStorageInfo( Environment.getDataDirectory( ) );
	}

	private String[] getStorageInfo( File path )
	{
		StatFs stat = new StatFs( path.getPath( ) );
		long blockSize = stat.getBlockSize( );

		String[] info = new String[2];
		info[0] = Formatter.formatFileSize( this, stat.getBlockCount( )
				* blockSize );
		info[1] = Formatter.formatFileSize( this, stat.getAvailableBlocks( )
				* blockSize );

		return info;
	}

	private String getNetAddressInfo( )
	{
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

			if ( !TextUtils.isEmpty( netAddress ) )
			{
				return netAddress;
			}
		}
		catch ( SocketException e )
		{
			Log.e( SysInfoManager.class.getName( ), e.getLocalizedMessage( ), e );
		}

		return getString( R.string.info_not_available );
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
		else if ( "send_report".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			final boolean[] items = new boolean[]{
					true, true, true, true, true, true
			};

			OnMultiChoiceClickListener selListener = new OnMultiChoiceClickListener( ) {

				public void onClick( DialogInterface dialog, int which,
						boolean isChecked )
				{
					items[which] = isChecked;
				}
			};

			OnClickListener sendListener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					boolean hasContent = false;

					for ( boolean b : items )
					{
						if ( b )
						{
							hasContent = true;
							break;
						}
					}

					if ( !hasContent )
					{
						Toast.makeText( SysInfoManager.this,
								R.string.no_report_item,
								Toast.LENGTH_SHORT ).show( );

						return;
					}

					final FormatArrayAdapter adapter = new FormatArrayAdapter( SysInfoManager.this,
							R.layout.send_item,
							new FormatItem[]{
									new FormatItem( getString( R.string.plain_text ) ),
									new FormatItem( getString( R.string.html ) ),
							} );

					OnClickListener listener = new OnClickListener( ) {

						public void onClick( DialogInterface dialog, int which )
						{
							FormatItem fi = adapter.getItem( which );

							sendReport( items, which, fi.compressed );
						}
					};

					new AlertDialog.Builder( SysInfoManager.this ).setTitle( R.string.send_report )
							.setAdapter( adapter, listener )
							.setInverseBackgroundForced( true )
							.create( )
							.show( );
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.send_report )
					.setMultiChoiceItems( new CharSequence[]{
							getString( R.string.tab_info ),
							getString( R.string.tab_apps ),
							getString( R.string.tab_procs ),
							getString( R.string.tab_netstat ),
							"Dmesg " + getString( R.string.log ), //$NON-NLS-1$
							"Logcat " + getString( R.string.log ) //$NON-NLS-1$
					},
							items,
							selListener )
					.setPositiveButton( android.R.string.ok, sendListener )
					.setNegativeButton( android.R.string.cancel, null )
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
			TextView txt = new TextView( this );
			txt.setGravity( Gravity.CENTER_HORIZONTAL );
			txt.setTextAppearance( this, android.R.style.TextAppearance_Medium );

			String href = "https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=qauck%2eaa%40gmail%2ecom&lc=US&item_name=Support%20Quick%20System%20Info&item_number=qsysinfo&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donate_LG%2egif%3aNonHosted"; //$NON-NLS-1$

			txt.setText( Html.fromHtml( getString( R.string.about_msg,
					getVersionName( getPackageManager( ), getPackageName( ) ),
					href ) ) );
			txt.setMovementMethod( LinkMovementMethod.getInstance( ) );

			new AlertDialog.Builder( this ).setTitle( R.string.app_name )
					.setIcon( R.drawable.icon )
					.setView( txt )
					.setNegativeButton( R.string.close, null )
					.create( )
					.show( );

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

	private void sendReport( final boolean[] items, final int format,
			final boolean compressed )
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
				String content = null;

				switch ( format )
				{
					case PLAINTEXT :
						content = generateTextReport( items );
						break;
					case HTML :
						content = generateHtmlReport( items );
						break;
				}

				if ( content != null && compressed )
				{
					content = createCompressedContent( SysInfoManager.this,
							content,
							format,
							"android_report" ); //$NON-NLS-1$
				}

				if ( content != null && !compressed )
				{
					handler.sendMessage( handler.obtainMessage( MSG_CHECK_FORCE_COMPRESSION,
							format,
							compressed ? 1 : 0,
							content ) );
				}
				else
				{
					handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
							format,
							compressed ? 1 : 0,
							content ) );
				}
			}
		} ).start( );
	}

	private String generateTextReport( boolean[] items )
	{
		StringBuffer sb = new StringBuffer( );

		LogViewer.createTextHeader( this, sb, "Android System Report - " //$NON-NLS-1$
				+ new Date( ).toLocaleString( ) );

		if ( items[BASIC_INFO] )
		{
			sb.append( getString( R.string.tab_info ) ).append( '\n' );
			sb.append( HEADER_SPLIT );

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.sd_storage ) )
					.append( "\n\t" ); //$NON-NLS-1$

			String[] info = getExternalStorageInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[1] ) );
			}
			sb.append( "\n\n" ); //$NON-NLS-1$

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.internal_storage ) )
					.append( "\n\t" ); //$NON-NLS-1$

			info = getInternalStorageInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[1] ) );
			}
			sb.append( "\n\n" ); //$NON-NLS-1$

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.memory ) )
					.append( "\n\t" ); //$NON-NLS-1$

			info = getMemInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[2] )
						+ getString( R.string.idle_info, info[1] ) );
			}
			sb.append( "\n\n" ); //$NON-NLS-1$

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.processor ) )
					.append( "\n\t" ) //$NON-NLS-1$
					.append( getCpuInfo( ) )
					.append( "\n\n" ); //$NON-NLS-1$

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.net_address ) )
					.append( "\n\t" ) //$NON-NLS-1$
					.append( getNetAddressInfo( ) )
					.append( "\n\n" ); //$NON-NLS-1$

			sb.append( '\n' );

			try
			{
				File f = new File( F_SCALE_FREQ );
				if ( f.exists( ) )
				{
					sb.append( getString( R.string.sc_freq ) );

					readRawText( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( getString( R.string.no_sc_freq_info ) )
							.append( '\n' );
				}

				sb.append( '\n' );

				f = new File( F_CPU_INFO );
				if ( f.exists( ) )
				{
					readRawText( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( getString( R.string.no_cpu_info ) )
							.append( '\n' );
				}

				sb.append( '\n' );

				f = new File( F_MEM_INFO );
				if ( f.exists( ) )
				{
					readRawText( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( getString( R.string.no_mem_info ) )
							.append( '\n' );
				}

				sb.append( '\n' );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}
		}

		if ( items[APPLICATIONS] )
		{
			sb.append( getString( R.string.tab_apps ) ).append( '\n' );
			sb.append( HEADER_SPLIT );

			PackageManager pm = getPackageManager( );
			List<PackageInfo> pkgs = pm.getInstalledPackages( 0 );

			if ( pkgs != null )
			{
				for ( PackageInfo pkg : pkgs )
				{
					sb.append( pkg.packageName ).append( " <" ) //$NON-NLS-1$
							.append( pkg.versionName )
							.append( " (" ) //$NON-NLS-1$
							.append( pkg.versionCode )
							.append( ")>" ); //$NON-NLS-1$

					if ( pkg.applicationInfo != null )
					{
						sb.append( "\t: " ) //$NON-NLS-1$
								.append( pkg.applicationInfo.loadLabel( pm ) )
								.append( " | " ) //$NON-NLS-1$
								.append( pkg.applicationInfo.flags )
								.append( " | " ) //$NON-NLS-1$
								.append( pkg.applicationInfo.sourceDir );
					}

					sb.append( '\n' );
				}
			}

			sb.append( '\n' );
		}

		if ( items[PROCESSES] )
		{
			sb.append( getString( R.string.tab_procs ) ).append( '\n' );
			sb.append( HEADER_SPLIT );

			ActivityManager am = (ActivityManager) this.getSystemService( ACTIVITY_SERVICE );
			List<RunningAppProcessInfo> procs = am.getRunningAppProcesses( );

			if ( procs != null )
			{
				PackageManager pm = getPackageManager( );

				for ( RunningAppProcessInfo proc : procs )
				{
					sb.append( '<' )
							.append( getImportance( proc ) )
							.append( "> [" ) //$NON-NLS-1$
							.append( proc.pid )
							.append( "]\t:\t" ); //$NON-NLS-1$

					sb.append( proc.processName );

					try
					{
						ApplicationInfo ai = pm.getApplicationInfo( proc.processName,
								0 );

						if ( ai != null )
						{
							CharSequence label = pm.getApplicationLabel( ai );

							if ( label != null
									&& !label.equals( proc.processName ) )
							{
								sb.append( " ( " ) //$NON-NLS-1$
										.append( label )
										.append( " )" ); //$NON-NLS-1$
							}
						}
					}
					catch ( NameNotFoundException e )
					{
						// ignore this error
					}

					sb.append( '\n' );
				}
			}

			sb.append( '\n' );
		}

		if ( items[NETSTATES] )
		{
			sb.append( getString( R.string.tab_netstat ) ).append( '\n' );
			sb.append( HEADER_SPLIT );

			try
			{
				readRawText( sb, new FileInputStream( "/proc/net/tcp" ) ); //$NON-NLS-1$

				sb.append( '\n' );

				readRawText( sb, new FileInputStream( "/proc/net/udp" ) ); //$NON-NLS-1$
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( '\n' );
		}

		if ( items[DMESG_LOG] )
		{
			sb.append( "Dmesg " + getString( R.string.log ) ).append( '\n' ); //$NON-NLS-1$
			sb.append( HEADER_SPLIT );

			try
			{
				Process proc = Runtime.getRuntime( ).exec( "dmesg" ); //$NON-NLS-1$

				readRawText( sb, proc.getInputStream( ) );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( '\n' );
		}

		if ( items[LOGCAT_LOG] )
		{
			sb.append( "Logcat " + getString( R.string.log ) ).append( '\n' ); //$NON-NLS-1$
			sb.append( HEADER_SPLIT );

			try
			{
				Process proc = Runtime.getRuntime( )
						.exec( "logcat -d -v time *:V" ); //$NON-NLS-1$

				readRawText( sb, proc.getInputStream( ) );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( '\n' );
		}

		return sb.toString( );
	}

	private String generateHtmlReport( boolean[] items )
	{
		StringBuffer sb = new StringBuffer( );

		LogViewer.createHtmlHeader( this,
				sb,
				escapeHtml( "Android System Report - " + new Date( ).toLocaleString( ) ) ); //$NON-NLS-1$

		if ( items[BASIC_INFO] )
		{
			sb.append( openHeaderRow )
					.append( getString( R.string.tab_info ) )
					.append( closeHeaderRow );

			sb.append( openRow )
					.append( getString( R.string.sd_storage ) )
					.append( nextColumn4 );

			String[] info = getExternalStorageInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[1] ) );
			}
			sb.append( closeRow );

			sb.append( openRow )
					.append( getString( R.string.internal_storage ) )
					.append( nextColumn4 );

			info = getInternalStorageInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[1] ) );
			}
			sb.append( closeRow );

			sb.append( openRow )
					.append( getString( R.string.memory ) )
					.append( nextColumn4 );

			info = getMemInfo( );
			if ( info == null )
			{
				sb.append( getString( R.string.info_not_available ) );
			}
			else
			{
				sb.append( getString( R.string.storage_summary,
						info[0],
						info[2] )
						+ getString( R.string.idle_info, info[1] ) );
			}
			sb.append( closeRow );

			sb.append( openRow )
					.append( getString( R.string.processor ) )
					.append( nextColumn4 )
					.append( escapeHtml( getCpuInfo( ) ) )
					.append( closeRow );

			sb.append( openRow )
					.append( getString( R.string.net_address ) )
					.append( nextColumn4 )
					.append( getNetAddressInfo( ) )
					.append( closeRow );

			sb.append( emptyRow );

			try
			{
				File f = new File( F_SCALE_FREQ );
				if ( f.exists( ) )
				{
					sb.append( openFullRow )
							.append( getString( R.string.sc_freq ) );

					readRawText( sb, new FileInputStream( f ) );

					sb.append( closeRow );
				}
				else
				{
					sb.append( openFullRow )
							.append( getString( R.string.no_sc_freq_info ) )
							.append( closeRow );
				}

				sb.append( emptyRow );

				f = new File( F_CPU_INFO );
				if ( f.exists( ) )
				{
					readRawHTML( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( openFullRow )
							.append( getString( R.string.no_cpu_info ) )
							.append( closeRow );
				}

				sb.append( emptyRow );

				f = new File( F_MEM_INFO );
				if ( f.exists( ) )
				{
					readRawHTML( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( openFullRow )
							.append( getString( R.string.no_mem_info ) )
							.append( closeRow );
				}

				sb.append( emptyRow );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}
		}

		if ( items[APPLICATIONS] )
		{
			sb.append( openHeaderRow )
					.append( getString( R.string.tab_apps ) )
					.append( closeHeaderRow );

			sb.append( openTitleRow ).append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.pkg_name ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.version ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.app_label ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.flags ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.source ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( closeRow );

			PackageManager pm = getPackageManager( );
			List<PackageInfo> pkgs = pm.getInstalledPackages( 0 );

			if ( pkgs != null )
			{
				for ( PackageInfo pkg : pkgs )
				{
					sb.append( openRow )
							.append( escapeHtml( pkg.packageName ) )
							.append( nextColumn )
							.append( escapeHtml( pkg.versionName ) )
							.append( " (" ) //$NON-NLS-1$
							.append( pkg.versionCode )
							.append( ')' );

					if ( pkg.applicationInfo != null )
					{
						sb.append( nextColumn )
								.append( escapeHtml( pkg.applicationInfo.loadLabel( pm )
										.toString( ) ) )
								.append( nextColumn )
								.append( pkg.applicationInfo.flags )
								.append( nextColumn )
								.append( escapeHtml( pkg.applicationInfo.sourceDir ) );
					}

					sb.append( closeRow );
				}
			}

			sb.append( emptyRow );
		}

		if ( items[PROCESSES] )
		{
			sb.append( openHeaderRow )
					.append( getString( R.string.tab_procs ) )
					.append( closeHeaderRow );

			sb.append( openTitleRow ).append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.importance ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.pid ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.proc_name ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( nextColumn )
					.append( "<b>" ) //$NON-NLS-1$
					.append( getString( R.string.app_label ) )
					.append( "</b>" ) //$NON-NLS-1$
					.append( closeRow );

			ActivityManager am = (ActivityManager) this.getSystemService( ACTIVITY_SERVICE );
			List<RunningAppProcessInfo> procs = am.getRunningAppProcesses( );

			if ( procs != null )
			{
				PackageManager pm = getPackageManager( );

				for ( RunningAppProcessInfo proc : procs )
				{
					sb.append( openRow )
							.append( getImportance( proc ) )
							.append( nextColumn )
							.append( proc.pid )
							.append( nextColumn )
							.append( escapeHtml( proc.processName ) );

					try
					{
						ApplicationInfo ai = pm.getApplicationInfo( proc.processName,
								0 );

						if ( ai != null )
						{
							CharSequence label = pm.getApplicationLabel( ai );

							if ( label != null
									&& !label.equals( proc.processName ) )
							{
								sb.append( nextColumn )
										.append( escapeHtml( label.toString( ) ) );
							}
						}
					}
					catch ( NameNotFoundException e )
					{
						// ignore this error
					}

					sb.append( closeRow );
				}
			}

			sb.append( emptyRow );
		}

		if ( items[NETSTATES] )
		{
			sb.append( openHeaderRow )
					.append( getString( R.string.tab_netstat ) )
					.append( closeHeaderRow );

			try
			{
				readRawHTML( sb, new FileInputStream( "/proc/net/tcp" ) ); //$NON-NLS-1$

				sb.append( emptyRow );

				readRawHTML( sb, new FileInputStream( "/proc/net/udp" ) ); //$NON-NLS-1$
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( emptyRow );
		}

		if ( items[DMESG_LOG] )
		{
			sb.append( openHeaderRow ).append( "Dmesg " //$NON-NLS-1$
					+ getString( R.string.log ) ).append( closeHeaderRow );

			try
			{
				Process proc = Runtime.getRuntime( ).exec( "dmesg" ); //$NON-NLS-1$

				readRawHTML( sb, proc.getInputStream( ) );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( emptyRow );
		}

		if ( items[LOGCAT_LOG] )
		{
			sb.append( openHeaderRow ).append( "Logcat " //$NON-NLS-1$
					+ getString( R.string.log ) ).append( closeHeaderRow );

			try
			{
				Process proc = Runtime.getRuntime( )
						.exec( "logcat -d -v time *:V" ); //$NON-NLS-1$

				readRawHTML( sb, proc.getInputStream( ) );
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			sb.append( emptyRow );
		}

		sb.append( "</table></font></body></html>" ); //$NON-NLS-1$

		return sb.toString( );
	}

	private String getImportance( RunningAppProcessInfo proc )
	{
		String impt = "Empty"; //$NON-NLS-1$

		switch ( proc.importance )
		{
			case RunningAppProcessInfo.IMPORTANCE_BACKGROUND :
				impt = "Background"; //$NON-NLS-1$
				break;
			case RunningAppProcessInfo.IMPORTANCE_FOREGROUND :
				impt = "Foreground"; //$NON-NLS-1$
				break;
			case RunningAppProcessInfo.IMPORTANCE_SERVICE :
				impt = "Service"; //$NON-NLS-1$
				break;
			case RunningAppProcessInfo.IMPORTANCE_VISIBLE :
				impt = "Visible"; //$NON-NLS-1$
				break;
		}

		return impt;
	}

	private static void readRawText( StringBuffer sb, InputStream input )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( input ), 8192 );

			String line;
			while ( ( line = reader.readLine( ) ) != null )
			{
				sb.append( line ).append( '\n' );
			}
		}
		catch ( Exception e )
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
				catch ( IOException e )
				{
					Log.e( SysInfoManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}
	}

	private static void readRawHTML( StringBuffer sb, InputStream input )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( input ), 8192 );

			String line;
			while ( ( line = reader.readLine( ) ) != null )
			{
				sb.append( openFullRow )
						.append( escapeHtml( line ) )
						.append( closeRow );
			}
		}
		catch ( Exception e )
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
				catch ( IOException e )
				{
					Log.e( SysInfoManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}
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

	private static String createCompressedContent( Activity context,
			String content, int format, String filePrefix )
	{
		String state = Environment.getExternalStorageState( );

		if ( Environment.MEDIA_MOUNTED.equals( state ) )
		{
			File path = Environment.getExternalStorageDirectory( );

			File tf = new File( path, "logs" ); //$NON-NLS-1$

			if ( !tf.exists( ) )
			{
				if ( !tf.mkdirs( ) )
				{
					Toast.makeText( context,
							context.getString( R.string.error_create_folder,
									tf.getAbsolutePath( ) ),
							Toast.LENGTH_SHORT ).show( );

					return null;
				}
			}

			File zf = new File( tf, filePrefix
					+ Math.abs( System.currentTimeMillis( ) )
					+ ".zip" ); //$NON-NLS-1$

			ZipOutputStream zos = null;
			try
			{
				zos = new ZipOutputStream( new BufferedOutputStream( new FileOutputStream( zf ) ) );

				String ext = ".txt"; //$NON-NLS-1$

				switch ( format )
				{
					case HTML :
						ext = ".html"; //$NON-NLS-1$
						break;
					case CSV :
						ext = ".csv"; //$NON-NLS-1$
						break;
				}

				zos.putNextEntry( new ZipEntry( filePrefix + ext ) );

				zos.write( content.getBytes( ) );

				zos.closeEntry( );

				return zf.getAbsolutePath( );
			}
			catch ( IOException e )
			{
				Log.e( SysInfoManager.class.getName( ),
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
						Log.e( SysInfoManager.class.getName( ),
								e.getLocalizedMessage( ),
								e );
					}
				}
			}
		}
		else
		{
			Toast.makeText( context, R.string.error_sdcard, Toast.LENGTH_SHORT )
					.show( );
		}

		return null;
	}

	private static void checkForceCompression( final Handler handler,
			final Activity context, final String content, final int format,
			final String title )
	{
		Log.d( SysInfoManager.class.getName( ), "VM Max size: " //$NON-NLS-1$
				+ Runtime.getRuntime( ).maxMemory( ) );

		Log.d( SysInfoManager.class.getName( ), "Sending content size: " //$NON-NLS-1$
				+ content.length( ) );

		if ( content != null && content.length( ) > 250 * 1024 )
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					String sendContent = createCompressedContent( context,
							content,
							format,
							title );

					handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
							format,
							1,
							sendContent ) );
				}
			};

			new AlertDialog.Builder( context ).setTitle( R.string.warning )
					.setMessage( R.string.size_warning )
					.setPositiveButton( android.R.string.ok, listener )
					.setNegativeButton( android.R.string.cancel, null )
					.create( )
					.show( );
		}
		else
		{
			handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
					format,
					0,
					content ) );
		}
	}

	private static void sendContent( Activity context, String subject,
			String content, boolean compressed )
	{
		Intent it = new Intent( Intent.ACTION_SEND );

		it.putExtra( Intent.EXTRA_SUBJECT, subject );

		if ( compressed )
		{
			it.putExtra( Intent.EXTRA_STREAM,
					Uri.fromFile( new File( content ) ) );
			it.putExtra( Intent.EXTRA_TEXT, subject );
			it.setType( "application/zip" ); //$NON-NLS-1$
		}
		else
		{
			it.putExtra( Intent.EXTRA_TEXT, content );
			it.setType( "text/plain" ); //$NON-NLS-1$
		}

		it = Intent.createChooser( it, null );

		context.startActivity( it );
	}

	private static String escapeCsv( String str )
	{
		if ( TextUtils.isEmpty( str ) || containsNone( str, CSV_SEARCH_CHARS ) )
		{
			return str;
		}

		StringBuffer sb = new StringBuffer( );

		sb.append( '"' );
		for ( int i = 0; i < str.length( ); i++ )
		{
			char c = str.charAt( i );
			if ( c == '"' )
			{
				sb.append( '"' ); // escape double quote
			}
			sb.append( c );
		}
		sb.append( '"' );

		return sb.toString( );
	}

	private static String escapeHtml( String str )
	{
		if ( TextUtils.isEmpty( str ) || containsNone( str, HTML_SEARCH_CHARS ) )
		{
			return str;
		}

		str = TextUtils.htmlEncode( str );

		if ( str.indexOf( '\n' ) == -1 )
		{
			return str;
		}

		StringBuffer sb = new StringBuffer( );
		char c;
		for ( int i = 0; i < str.length( ); i++ )
		{
			c = str.charAt( i );

			if ( c == '\n' )
			{
				sb.append( "<br>" ); //$NON-NLS-1$
			}
			else
			{
				sb.append( c );
			}
		}

		return sb.toString( );
	}

	private static boolean containsNone( String str, char[] invalidChars )
	{
		int strSize = str.length( );
		int validSize = invalidChars.length;

		for ( int i = 0; i < strSize; i++ )
		{
			char ch = str.charAt( i );
			for ( int j = 0; j < validSize; j++ )
			{
				if ( invalidChars[j] == ch )
				{
					return false;
				}
			}
		}

		return true;
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
					case MSG_CONTENT_READY :

						sendEmptyMessage( MSG_DISMISS_PROGRESS );

						String content = (String) msg.obj;

						if ( content == null )
						{
							Toast.makeText( LogViewer.this,
									R.string.no_log_info,
									Toast.LENGTH_SHORT ).show( );
						}
						else
						{
							sendContent( LogViewer.this,
									"Android Device Log - " + new Date( ).toLocaleString( ), //$NON-NLS-1$
									content,
									msg.arg2 == 1 );
						}

						break;
					case MSG_CHECK_FORCE_COMPRESSION :

						sendEmptyMessage( MSG_DISMISS_PROGRESS );

						checkForceCompression( this,
								LogViewer.this,
								(String) msg.obj,
								msg.arg1,
								"android_log" ); //$NON-NLS-1$

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
			else if ( item.getItemId( ) == R.id.mi_send_log )
			{
				final FormatArrayAdapter adapter = new FormatArrayAdapter( this,
						R.layout.send_item,
						new FormatItem[]{
								new FormatItem( getString( R.string.plain_text ) ),
								new FormatItem( getString( R.string.html ) ),
								new FormatItem( getString( R.string.csv ) ),
						} );

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						FormatItem fi = adapter.getItem( which );

						sendLog( fi.compressed, which );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.send_log )
						.setAdapter( adapter, listener )
						.setInverseBackgroundForced( true )
						.create( )
						.show( );

				return true;
			}

			return false;
		}

		private void sendLog( final boolean compressed, final int format )
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
					String content = null;

					switch ( format )
					{
						case PLAINTEXT :
							content = collectTextLogContent( );
							break;
						case HTML :
							content = collectHtmlLogContent( );
							break;
						case CSV :
							content = collectCSVLogContent( );
							break;
					}

					if ( content != null && compressed )
					{
						content = createCompressedContent( LogViewer.this,
								content,
								format,
								"android_log" ); //$NON-NLS-1$
					}

					if ( content != null && !compressed )
					{
						handler.sendMessage( handler.obtainMessage( MSG_CHECK_FORCE_COMPRESSION,
								format,
								compressed ? 1 : 0,
								content ) );
					}
					else
					{
						handler.sendMessage( handler.obtainMessage( MSG_CONTENT_READY,
								format,
								compressed ? 1 : 0,
								content ) );
					}
				}
			} ).start( );
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

		private String collectTextLogContent( )
		{
			StringBuffer sb = new StringBuffer( );

			createTextHeader( this, sb, "Android Device Log - " //$NON-NLS-1$
					+ new Date( ).toLocaleString( ) );

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
							sb.append( escapeCsv( log.time ) );
						}

						sb.append( ',' )
								.append( escapeCsv( log.msg ) )
								.append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( log.level ).append( ',' );

							if ( log.time != null )
							{
								sb.append( escapeCsv( log.time ) );
							}

							sb.append( ',' )
									.append( escapeCsv( s ) )
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
						sb.append( escapeCsv( log.time ) )
								.append( ',' )
								.append( log.level )
								.append( ',' )
								.append( escapeCsv( log.tag ) )
								.append( ',' )
								.append( log.pid )
								.append( ',' )
								.append( escapeCsv( log.msg ) )
								.append( '\n' );
					}
					else
					{
						for ( String s : log.msgList )
						{
							sb.append( escapeCsv( log.time ) )
									.append( ',' )
									.append( log.level )
									.append( ',' )
									.append( escapeCsv( log.tag ) )
									.append( ',' )
									.append( log.pid )
									.append( ',' )
									.append( escapeCsv( s ) )
									.append( '\n' );
						}
					}
				}
			}

			return sb.toString( );
		}

		private String collectHtmlLogContent( )
		{
			StringBuffer sb = new StringBuffer( );

			createHtmlHeader( this,
					sb,
					escapeHtml( "Android Device Log - " + new Date( ).toLocaleString( ) ) ); //$NON-NLS-1$

			ListAdapter adapter = getListView( ).getAdapter( );
			int cnt = adapter.getCount( );

			if ( dmesgMode )
			{
				sb.append( openHeaderRow ).append( "Dmesg " //$NON-NLS-1$
						+ getString( R.string.log ) ).append( closeHeaderRow );

				sb.append( openTitleRow ).append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.log_level ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.time ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.message ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( closeRow );

				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					sb.append( openRow ).append( "<" + log.level + ">" ) //$NON-NLS-1$ //$NON-NLS-2$
							.append( nextColumn );

					sb.append( log.time == null ? "&nbsp;" //$NON-NLS-1$
							: escapeHtml( log.time ) ).append( nextColumn );

					sb.append( escapeHtml( log.getMsg( ) ) ).append( closeRow );
				}
			}
			else
			{
				sb.append( openHeaderRow ).append( "Logcat " //$NON-NLS-1$
						+ getString( R.string.log ) ).append( closeHeaderRow );

				sb.append( openTitleRow ).append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.time ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.log_level ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.tag ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.pid ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( nextColumn )
						.append( "<b>" ) //$NON-NLS-1$
						.append( getString( R.string.message ) )
						.append( "</b>" ) //$NON-NLS-1$
						.append( closeRow );

				for ( int i = 0; i < cnt; i++ )
				{
					LogItem log = (LogItem) adapter.getItem( i );

					sb.append( openRow )
							.append( escapeHtml( log.time ) )
							.append( nextColumn );

					sb.append( log.level ).append( nextColumn );
					sb.append( escapeHtml( log.tag ) ).append( nextColumn );
					sb.append( log.pid ).append( nextColumn );

					sb.append( escapeHtml( log.getMsg( ) ) ).append( closeRow );
				}
			}

			sb.append( emptyRow );

			sb.append( "</table></font></body></html>" ); //$NON-NLS-1$

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
					ArrayList<LogItem> logs = dmesgMode ? collectDLog( getDLogLevel( ) )
							: collectCLog( getCLogLevel( ),
									getCTagFilter( ),
									getPIDFilter( ) );

					handler.sendMessage( handler.obtainMessage( MSG_INIT_OK,
							logs ) );
				}
			} ).start( );
		}

		private static void createTextHeader( Context context, StringBuffer sb,
				String title )
		{
			sb.append( title ).append( "\n\n" ); //$NON-NLS-1$

			sb.append( context.getString( R.string.collector_head,
					context.getString( R.string.app_name ),
					getVersionName( context.getPackageManager( ),
							context.getPackageName( ) ) ) );

			sb.append( context.getString( R.string.device ) ).append( ": " ) //$NON-NLS-1$
					.append( Build.DEVICE )
					.append( '\n' )
					.append( context.getString( R.string.model ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Build.MODEL )
					.append( '\n' )
					.append( context.getString( R.string.product ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Build.PRODUCT )
					.append( '\n' )
					.append( context.getString( R.string.brand ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Build.BRAND )
					.append( '\n' )
					.append( context.getString( R.string.release ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Build.VERSION.RELEASE )
					.append( '\n' )
					.append( context.getString( R.string.build ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Build.DISPLAY )
					.append( '\n' )
					.append( context.getString( R.string.locale ) )
					.append( ": " ) //$NON-NLS-1$
					.append( Locale.getDefault( ).toString( ) )
					.append( "\n\n" ); //$NON-NLS-1$

			try
			{
				readRawText( sb, new FileInputStream( F_VERSION ) );

				sb.append( '\n' );
			}
			catch ( Exception e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}
		}

		private static void createHtmlHeader( Context context, StringBuffer sb,
				String title )
		{
			sb.append( "<html><head><title>" ) //$NON-NLS-1$
					.append( title )
					.append( "</title><meta http-equiv=\"Content-type\" content=\"text/html;charset=UTF-8\"/></head>\n" ) //$NON-NLS-1$
					.append( "<body bgcolor=FFFFFF><font face=\"Verdana\" color=\"#000000\">\n" ) //$NON-NLS-1$
					.append( "<table border=0 width=\"100%\" cellspacing=\"2\" cellpadding=\"2\">\n" ) //$NON-NLS-1$
					.append( "<tr align=\"left\">" ) //$NON-NLS-1$
					.append( "<td colspan=5>" ) //$NON-NLS-1$
					.append( "<table border=0 width=\"100%\" cellspacing=\"2\" cellpadding=\"2\">" ) //$NON-NLS-1$
					.append( "<tr><td width=60>" ) //$NON-NLS-1$
					.append( "<a href=\"http://code.google.com/p/qsysinfo/\">" ) //$NON-NLS-1$
					.append( "<img src=\"http://code.google.com/p/qsysinfo/logo?logo_id=1261652286\" border=0></a>" ) //$NON-NLS-1$
					.append( "</td><td valign=\"bottom\">" ) //$NON-NLS-1$
					.append( "<h3>" ) //$NON-NLS-1$
					.append( title )
					.append( "</h3></td></tr></table></td></tr>\n" ); //$NON-NLS-1$

			sb.append( "<tr align=\"left\"><td colspan=5><font color=\"#a0a0a0\"><small>" ); //$NON-NLS-1$
			sb.append( escapeHtml( context.getString( R.string.collector_head,
					context.getString( R.string.app_name ),
					getVersionName( context.getPackageManager( ),
							context.getPackageName( ) ) ) ) );
			sb.append( "</small></font></td></tr>\n" ); //$NON-NLS-1$

			sb.append( openHeaderRow )
					.append( context.getString( R.string.device_info ) )
					.append( closeHeaderRow );
			sb.append( openRow )
					.append( context.getString( R.string.device ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.DEVICE ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.model ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.MODEL ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.product ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.PRODUCT ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.brand ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.BRAND ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.release ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.VERSION.RELEASE ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.build ) )
					.append( nextColumn4 )
					.append( escapeHtml( Build.DISPLAY ) )
					.append( closeRow );
			sb.append( openRow )
					.append( context.getString( R.string.locale ) )
					.append( nextColumn4 )
					.append( escapeHtml( Locale.getDefault( ).toString( ) ) )
					.append( closeRow );

			sb.append( emptyRow );

			sb.append( openHeaderRow )
					.append( context.getString( R.string.sys_version ) )
					.append( closeHeaderRow );

			try
			{
				readRawHTML( sb, new FileInputStream( F_VERSION ) );

				sb.append( emptyRow );
			}
			catch ( Exception e )
			{
				Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
			}
		}

		private static LogItem parseDLog( String line, char targetLevel )
		{
			int levelOffset = line.indexOf( '>' );

			if ( levelOffset < 1 )
			{
				Log.d( LogViewer.class.getName( ),
						"Unexpected dmesg line: " + line ); //$NON-NLS-1$

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
					else
					{
						Log.d( LogViewer.class.getName( ),
								"Unexpected dmesg time value: " + line ); //$NON-NLS-1$
					}
				}
				else
				{
					Log.d( LogViewer.class.getName( ),
							"Unexpected dmesg time format: " + line ); //$NON-NLS-1$
				}
			}

			log.msg = line.substring( log.time == null ? ( levelOffset + 1 )
					: ( timeOffset + 1 ) ).trim( );

			return log;
		}

		private static LogItem parseCLog( String line, String tagFilter,
				int pidFilter )
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

		private static String formatDLog( LogItem log )
		{
			return "<" + log.level + "> " //$NON-NLS-1$ //$NON-NLS-2$
					+ ( log.time == null ? "" : ( "[" + log.time + "] " ) ); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		private static String formatCLog( LogItem log )
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

		private static ArrayList<LogItem> collectDLog( int logLevel )
		{
			char dl = (char) ( logLevel + 0x30 );

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

		private static ArrayList<LogItem> collectCLog( int logLevel,
				String tagFilter, int pidFilter )
		{
			char cl = 'V';

			switch ( logLevel )
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
					new AlertDialog.Builder( this ).setTitle( R.string.log_level )
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
					new AlertDialog.Builder( this ).setTitle( R.string.log_level )
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

	/**
	 * FormatItem
	 */
	private static final class FormatItem
	{

		String format;
		boolean compressed;

		FormatItem( String format )
		{
			this.format = format;
			this.compressed = false;
		}
	}

	/**
	 * FormatArrayAdapter
	 */
	private static final class FormatArrayAdapter extends
			ArrayAdapter<FormatItem>
	{

		private Activity context;

		FormatArrayAdapter( Activity context, int textViewResourceId,
				FormatItem[] objects )
		{
			super( context, textViewResourceId, objects );

			this.context = context;
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent )
		{
			View view;

			if ( convertView == null )
			{
				view = context.getLayoutInflater( )
						.inflate( R.layout.send_item, parent, false );
			}
			else
			{
				view = convertView;
			}

			final FormatItem fi = getItem( position );

			TextView txt_format = (TextView) view.findViewById( R.id.txt_format );
			txt_format.setText( fi.format );

			final TextView txt_hint = (TextView) view.findViewById( R.id.txt_hint );
			txt_hint.setTextColor( context.getResources( )
					.getColor( fi.compressed ? android.R.color.secondary_text_light
							: android.R.color.secondary_text_dark ) );

			View hintArea = view.findViewById( R.id.ll_compress );

			hintArea.setOnClickListener( new View.OnClickListener( ) {

				public void onClick( View v )
				{
					fi.compressed = !fi.compressed;

					txt_hint.setTextColor( context.getResources( )
							.getColor( fi.compressed ? android.R.color.secondary_text_light
									: android.R.color.secondary_text_dark ) );
				}
			} );

			return view;
		}
	}
}