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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import org.uguess.android.sysinfo.WidgetProvider.InfoWidget;
import org.uguess.android.sysinfo.WidgetProvider.TaskWidget;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * SysInfoManager
 */
public final class SysInfoManager extends PreferenceActivity implements
		Constants
{

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
	private static final String F_MOUNT_INFO = "/proc/mounts"; //$NON-NLS-1$

	private static final String HEADER_SPLIT = "========================================================================================\n"; //$NON-NLS-1$
	private static final String openFullRow = "<tr align=\"left\" valign=\"top\"><td colspan=5><small>"; //$NON-NLS-1$

	static final String openHeaderRow = "<tr align=\"left\" bgcolor=\"#E0E0FF\"><td><b>"; //$NON-NLS-1$
	static final String closeHeaderRow = "</b></td><td colspan=4/></tr>\n"; //$NON-NLS-1$
	static final String openRow = "<tr align=\"left\" valign=\"top\"><td nowrap><small>"; //$NON-NLS-1$
	static final String openTitleRow = "<tr bgcolor=\"#E0E0E0\" align=\"left\" valign=\"top\"><td><small>"; //$NON-NLS-1$
	static final String closeRow = "</small></td></tr>\n"; //$NON-NLS-1$
	static final String nextColumn = "</small></td><td><small>"; //$NON-NLS-1$
	static final String nextColumn4 = "</small></td><td colspan=4><small>"; //$NON-NLS-1$
	static final String emptyRow = "<tr><td>&nbsp;</td></tr>\n"; //$NON-NLS-1$

	static final String PREF_KEY_SHOW_INFO_ICON = "show_info_icon"; //$NON-NLS-1$
	static final String PREF_KEY_SHOW_TASK_ICON = "show_task_icon"; //$NON-NLS-1$
	static final String PREF_KEY_AUTO_START_ICON = "auto_start_icon"; //$NON-NLS-1$
	static final String PREF_KEY_DEFAULT_EMAIL = "default_email"; //$NON-NLS-1$
	static final String PREF_KEY_DEFAULT_TAB = "default_tab"; //$NON-NLS-1$
	static final String PREF_KEY_WIDGET_DISABLED = "widget_disabled"; //$NON-NLS-1$

	private static final int BASIC_INFO = 0;
	private static final int APPLICATIONS = 1;
	private static final int PROCESSES = 2;
	private static final int NETSTATES = 3;
	private static final int DMESG_LOG = 4;
	private static final int LOGCAT_LOG = 5;

	private static final int WIDGET_BAR = 0;
	private static final int WIDGET_INFO = 1;
	private static final int WIDGET_TASK = 2;

	ProgressDialog progress;

	volatile boolean aborted;

	private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver( ) {

		@Override
		public void onReceive( Context context, Intent intent )
		{
			String action = intent.getAction( );
			if ( Intent.ACTION_BATTERY_CHANGED.equals( action ) )
			{
				int level = intent.getIntExtra( "level", 0 ); //$NON-NLS-1$
				int scale = intent.getIntExtra( "scale", 100 ); //$NON-NLS-1$

				String lStr = String.valueOf( level * 100 / scale ) + '%';

				int health = intent.getIntExtra( "health", //$NON-NLS-1$
						BatteryManager.BATTERY_HEALTH_UNKNOWN );

				String hStr = getString( R.string.unknown );

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

				findPreference( "battery_level" ).setSummary( hStr //$NON-NLS-1$
						+ " (" //$NON-NLS-1$
						+ lStr
						+ ")" ); //$NON-NLS-1$
			}
		}
	};

	Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_CONTENT_READY :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.handleMsgSendContentReady( (String) msg.obj,
							"Android System Report - ", //$NON-NLS-1$
							SysInfoManager.this,
							msg.arg2 == 1 );

					break;
				case MSG_CHECK_FORCE_COMPRESSION :

					sendEmptyMessage( MSG_DISMISS_PROGRESS );

					Util.checkForceCompression( this,
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
				case MSG_TOAST :

					Util.shortToast( SysInfoManager.this, (String) msg.obj );
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

		Intent it = getAboutSettingsIntent( );

		if ( it == null )
		{
			( (PreferenceGroup) getPreferenceScreen( ).getPreference( getPreferenceScreen( ).getPreferenceCount( ) - 1 ) ).removePreference( findPreference( "more_info" ) ); //$NON-NLS-1$
		}
	}

	@Override
	protected void onDestroy( )
	{
		if ( progress != null )
		{
			progress.dismiss( );
			progress = null;
		}
		super.onDestroy( );
	}

	@Override
	protected void onResume( )
	{
		aborted = false;

		super.onResume( );

		registerReceiver( mBatteryInfoReceiver,
				new IntentFilter( Intent.ACTION_BATTERY_CHANGED ) );

		updateInfo( );
	}

	@Override
	protected void onPause( )
	{
		aborted = true;

		handler.removeMessages( MSG_CHECK_FORCE_COMPRESSION );
		handler.removeMessages( MSG_CONTENT_READY );

		unregisterReceiver( mBatteryInfoReceiver );

		super.onPause( );
	}

	private void updateInfo( )
	{
		findPreference( "processor" ).setSummary( getCpuInfo( ) ); //$NON-NLS-1$

		String[] mi = getMemInfo( );
		findPreference( "memory" ).setSummary( mi == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: ( getString( R.string.storage_summary, mi[0], mi[2] ) + getString( R.string.idle_info,
						mi[1] ) ) );

		String[] si = getExternalStorageInfo( );
		findPreference( "sd_storage" ).setSummary( si == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, si[0], si[1] ) );

		si = getA2SDStorageInfo( );
		findPreference( "app2sd_storage" ).setSummary( si == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, si[0], si[1] ) );

		si = getInternalStorageInfo( );
		findPreference( "internal_storage" ).setSummary( si == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, si[0], si[1] ) );

		si = getSystemStorageInfo( );
		findPreference( "system_storage" ).setSummary( si == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, si[0], si[1] ) );

		si = getCacheStorageInfo( );
		findPreference( "cache_storage" ).setSummary( si == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: getString( R.string.storage_summary, si[0], si[1] ) );

		String nInfo = getNetAddressInfo( );
		findPreference( "net_address" ).setSummary( nInfo == null ? getString( R.string.info_not_available ) //$NON-NLS-1$
				: nInfo );
		findPreference( "net_address" ).setEnabled( nInfo != null ); //$NON-NLS-1$

		int s = getSensorState( );
		findPreference( "sensors" ).setSummary( getSensorInfo( s ) ); //$NON-NLS-1$
		findPreference( "sensors" ).setEnabled( s > 0 ); //$NON-NLS-1$

		// int[] gs = getGpsState( );
		//		findPreference( "gps" ).setSummary( getGpsInfo( gs ) ); //$NON-NLS-1$
		//		findPreference( "gps" ).setEnabled( gs != null ); //$NON-NLS-1$
	}

	private String[] getMemInfo( )
	{
		long[] state = getMemState( this );

		if ( state == null )
		{
			return null;
		}

		String[] mem = new String[state.length];

		for ( int i = 0, size = mem.length; i < size; i++ )
		{
			if ( state[i] == -1 )
			{
				mem[i] = getString( R.string.info_not_available );
			}
			else
			{
				mem[i] = Formatter.formatFileSize( this, state[i] );
			}
		}

		return mem;
	}

	/**
	 * @return [total, idle, free]
	 */
	static long[] getMemState( Context ctx )
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

			long[] mem = new long[3];

			mem[0] = extractMemCount( totalMsg );
			mem[1] = extractMemCount( freeMsg );

			ActivityManager am = (ActivityManager) ctx.getSystemService( Context.ACTIVITY_SERVICE );
			MemoryInfo mi = new MemoryInfo( );
			am.getMemoryInfo( mi );
			mem[2] = mi.availMem;

			return mem;
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

	static long extractMemCount( String line )
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

						return size;
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

		return -1;
	}

	private String getCpuInfo( )
	{
		String[] stat = getCpuState( );

		if ( stat != null && stat.length == 2 )
		{
			if ( stat[1] == null )
			{
				return stat[0];
			}
			else
			{
				return stat[0] + "  " + stat[1]; //$NON-NLS-1$
			}
		}

		return getResources( ).getString( R.string.info_not_available );
	}

	/**
	 * @return [model, mips]
	 */
	static String[] getCpuState( )
	{
		BufferedReader reader = null;

		try
		{
			String line;
			String processor = null;
			String mips = null;
			String model = null;

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

						mips = String.valueOf( freq / 1000 );
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
				if ( processor == null && line.startsWith( "Processor" ) ) //$NON-NLS-1$
				{
					processor = line;
				}
				else if ( mips == null && line.startsWith( "BogoMIPS" ) ) //$NON-NLS-1$
				{
					mips = line;
				}
				if ( model == null && line.startsWith( "model name" ) ) //$NON-NLS-1$
				{
					model = line;
				}

				if ( model != null || ( processor != null && mips != null ) )
				{
					break;
				}
			}

			if ( model != null )
			{
				int idx = model.indexOf( ':' );
				if ( idx != -1 )
				{
					return new String[]{
							model.substring( idx + 1 ).trim( ), null
					};
				}
				else
				{
					Log.e( SysInfoManager.class.getName( ),
							"Unexpected processor format: " + model ); //$NON-NLS-1$
				}
			}
			else if ( processor != null && mips != null )
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

					return new String[]{
							processor, mips + "MHz" //$NON-NLS-1$
					};
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

	private String getSensorInfo( int state )
	{
		if ( state == -1 )
		{
			return getString( R.string.info_not_available );
		}

		if ( state > 1 )
		{
			return getString( R.string.sensor_info2, state );
		}
		else
		{
			return getString( R.string.sensor_info, state );
		}
	}

	private int getSensorState( )
	{
		SensorManager sm = (SensorManager) getSystemService( Context.SENSOR_SERVICE );

		if ( sm != null )
		{
			List<Sensor> ss = sm.getSensorList( Sensor.TYPE_ALL );

			int c = 0;

			if ( ss != null )
			{
				c = ss.size( );
			}

			return c;
		}

		return -1;
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

	private String[] getA2SDStorageInfo( )
	{
		String state = Environment.getExternalStorageState( );

		if ( Environment.MEDIA_MOUNTED_READ_ONLY.equals( state )
				|| Environment.MEDIA_MOUNTED.equals( state ) )
		{
			// here we just guess if it's app2sd enabled, this should work for
			// most app2sd enabled roms, but may not all.

			File f = new File( "/dev/block/mmcblk0p2" ); //$NON-NLS-1$

			if ( f.exists( ) )
			{
				BufferedReader reader = null;
				String mountPoint = null;

				try
				{
					reader = new BufferedReader( new InputStreamReader( new FileInputStream( F_MOUNT_INFO ) ),
							1024 );

					String line;

					while ( ( line = reader.readLine( ) ) != null )
					{
						if ( line.startsWith( "/dev/block/mmcblk0p2 " ) ) //$NON-NLS-1$
						{
							// 21==length of the above string
							int idx = line.indexOf( ' ', 21 );

							if ( idx != -1 )
							{
								mountPoint = line.substring( 21, idx ).trim( );
							}

							break;
						}
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

				if ( mountPoint != null )
				{
					f = new File( mountPoint );

					if ( f.exists( ) && f.isDirectory( ) )
					{
						return getStorageInfo( f );
					}
				}
			}
		}

		return getSystemA2SDStorageInfo( );
	}

	/**
	 * This checks the built-in app2sd storage info supported since Froyo
	 */
	private String[] getSystemA2SDStorageInfo( )
	{
		final PackageManager pm = getPackageManager( );
		List<ApplicationInfo> allApps = pm.getInstalledApplications( 0 );

		long total = 0;
		long free = 0;

		for ( int i = 0, size = allApps.size( ); i < size; i++ )
		{
			ApplicationInfo info = allApps.get( i );

			if ( ( info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE ) != 0 )
			{
				String src = info.sourceDir;

				if ( src != null )
				{
					File srcFile = new File( src );

					if ( srcFile.canRead( ) )
					{
						try
						{
							StatFs stat = new StatFs( srcFile.getAbsolutePath( ) );
							long blockSize = stat.getBlockSize( );

							total += stat.getBlockCount( ) * blockSize;
							free += stat.getAvailableBlocks( ) * blockSize;
						}
						catch ( Exception e )
						{
							Log.e( SysInfoManager.class.getName( ),
									"Cannot access path: " //$NON-NLS-1$
											+ srcFile.getAbsolutePath( ),
									e );
						}
					}
				}
			}
		}

		if ( total > 0 )
		{
			String[] info = new String[2];
			info[0] = Formatter.formatFileSize( this, total );
			info[1] = Formatter.formatFileSize( this, free );

			return info;
		}

		return null;
	}

	private String[] getInternalStorageInfo( )
	{
		return getStorageInfo( Environment.getDataDirectory( ) );
	}

	private String[] getSystemStorageInfo( )
	{
		return getStorageInfo( Environment.getRootDirectory( ) );
	}

	private String[] getCacheStorageInfo( )
	{
		return getStorageInfo( Environment.getDownloadCacheDirectory( ) );
	}

	private String[] getStorageInfo( File path )
	{
		if ( path != null )
		{
			try
			{
				StatFs stat = new StatFs( path.getAbsolutePath( ) );
				long blockSize = stat.getBlockSize( );

				String[] info = new String[2];
				info[0] = Formatter.formatFileSize( this, stat.getBlockCount( )
						* blockSize );
				info[1] = Formatter.formatFileSize( this,
						stat.getAvailableBlocks( ) * blockSize );

				return info;
			}
			catch ( Exception e )
			{
				Log.e( SysInfoManager.class.getName( ), "Cannot access path: " //$NON-NLS-1$
						+ path.getAbsolutePath( ), e );
			}
		}

		return null;
	}

	static String getNetAddressInfo( )
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

		return null;
	}

	@Override
	public boolean onPreferenceTreeClick( PreferenceScreen preferenceScreen,
			Preference preference )
	{
		if ( "net_address".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent it = new Intent( this, NetworkInfoActivity.class );
			startActivityForResult( it, 1 );

			return true;
		}
		else if ( "processor".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent it = new Intent( this, CpuInfoActivity.class );
			startActivityForResult( it, 1 );

			return true;
		}
		else if ( "memory".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent it = new Intent( this, MemInfoActivity.class );
			startActivityForResult( it, 1 );

			return true;
		}
		else if ( "battery_level".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent it = new Intent( this, BatteryInfoActivity.class );
			startActivityForResult( it, 1 );

			return true;
		}
		else if ( "sensors".equals( preference.getKey( ) ) ) //$NON-NLS-1$
		{
			Intent it = new Intent( this, SensorInfoActivity.class );
			startActivityForResult( it, 1 );

			return true;
		}
		else if ( "refresh_status".equals( preference.getKey( ) ) ) //$NON-NLS-1$
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
						Util.shortToast( SysInfoManager.this,
								R.string.no_report_item );

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
			Intent it = getAboutSettingsIntent( );

			if ( it != null )
			{
				startActivity( it );
			}
			else
			{
				Log.d( SysInfoManager.class.getName( ),
						"Failed to resolve activity for DeviceInfoSettings" ); //$NON-NLS-1$
			}
			return true;
		}

		return false;
	}

	private Intent getAboutSettingsIntent( )
	{
		Intent it = new Intent( Intent.ACTION_VIEW );

		// try the htc specifc settings first to avoid some broken manifest
		// issue on certain htc models
		it.setClassName( "com.android.settings", //$NON-NLS-1$
				"com.android.settings.framework.aboutphone.HtcAboutPhoneSettings" ); //$NON-NLS-1$

		List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
				0 );

		if ( acts.size( ) > 0 )
		{
			return it;
		}
		else
		{
			// try the standard settings
			it.setClassName( "com.android.settings", //$NON-NLS-1$
					"com.android.settings.DeviceInfoSettings" ); //$NON-NLS-1$

			acts = getPackageManager( ).queryIntentActivities( it, 0 );

			if ( acts.size( ) > 0 )
			{
				return it;
			}
		}

		return null;
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 2 && data != null )
		{
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_INFO_ICON );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_TASK_ICON );
			Util.updateBooleanOption( data,
					this,
					PREF_KEY_AUTO_START_ICON,
					false );
			Util.updateStringOption( data, this, PREF_KEY_DEFAULT_EMAIL );
			Util.updateIntOption( data, this, PREF_KEY_DEFAULT_TAB, 0 );
			Util.updateStringOption( data, this, PREF_KEY_WIDGET_DISABLED );
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE, MI_ABOUT, Menu.NONE, R.string.about );
		mi.setIcon( android.R.drawable.ic_menu_info_details );

		mi = menu.add( Menu.NONE, MI_HELP, Menu.NONE, R.string.help );
		mi.setIcon( android.R.drawable.ic_menu_help );

		mi = menu.add( Menu.NONE, MI_PREFERENCE, Menu.NONE, R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		mi = menu.add( Menu.NONE, MI_EXIT, Menu.NONE, R.string.exit );
		mi.setIcon( android.R.drawable.ic_lock_power_off );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_PREFERENCE )
		{
			Intent it = new Intent( this, InfoSettings.class );

			it.putExtra( PREF_KEY_SHOW_INFO_ICON,
					Util.getBooleanOption( this, PREF_KEY_SHOW_INFO_ICON ) );
			it.putExtra( PREF_KEY_SHOW_TASK_ICON,
					Util.getBooleanOption( this, PREF_KEY_SHOW_TASK_ICON ) );
			it.putExtra( PREF_KEY_AUTO_START_ICON, Util.getBooleanOption( this,
					PREF_KEY_AUTO_START_ICON,
					false ) );
			it.putExtra( PREF_KEY_DEFAULT_EMAIL,
					Util.getStringOption( this, PREF_KEY_DEFAULT_EMAIL, null ) );
			it.putExtra( PREF_KEY_DEFAULT_TAB,
					Util.getIntOption( this, PREF_KEY_DEFAULT_TAB, 0 ) );
			it.putExtra( PREF_KEY_WIDGET_DISABLED,
					Util.getStringOption( this, PREF_KEY_WIDGET_DISABLED, null ) );

			startActivityForResult( it, 2 );

			return true;
		}
		else if ( item.getItemId( ) == MI_HELP )
		{
			Intent it = new Intent( Intent.ACTION_VIEW );

			String target = "http://code.google.com/p/qsysinfo/wiki/FeaturesTextOnly#Introduction"; //$NON-NLS-1$

			ConnectivityManager cm = (ConnectivityManager) getSystemService( Activity.CONNECTIVITY_SERVICE );

			NetworkInfo info = cm.getNetworkInfo( ConnectivityManager.TYPE_WIFI );

			if ( info != null && info.isConnected( ) )
			{
				target = "http://code.google.com/p/qsysinfo/wiki/Features#Introduction"; //$NON-NLS-1$
			}

			it.setData( Uri.parse( target ) );

			it = Intent.createChooser( it, null );

			startActivity( it );

			return true;
		}
		else if ( item.getItemId( ) == MI_ABOUT )
		{
			ScrollView sv = new ScrollView( this );

			TextView txt = new TextView( this );
			txt.setGravity( Gravity.CENTER_HORIZONTAL );
			txt.setTextAppearance( this, android.R.style.TextAppearance_Medium );

			sv.addView( txt );

			String href = "http://qsysinfo.appspot.com/donate.jsp"; //$NON-NLS-1$

			txt.setText( Html.fromHtml( getString( R.string.about_msg,
					getVersionName( getPackageManager( ), getPackageName( ) ),
					href ) ) );
			txt.setMovementMethod( LinkMovementMethod.getInstance( ) );

			new AlertDialog.Builder( this ).setTitle( R.string.app_name )
					.setIcon( R.drawable.icon_m )
					.setView( sv )
					.setNegativeButton( R.string.close, null )
					.create( )
					.show( );

			return true;
		}
		else if ( item.getItemId( ) == MI_EXIT )
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					Util.killSelf( handler,
							SysInfoManager.this,
							(ActivityManager) getSystemService( ACTIVITY_SERVICE ),
							getPackageName( ) );
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.prompt )
					.setMessage( R.string.exit_prompt )
					.setPositiveButton( android.R.string.yes, listener )
					.setNegativeButton( android.R.string.no, null )
					.create( )
					.show( );

			return true;
		}

		return false;
	}

	void showLog( boolean dmesg )
	{
		Intent it = new Intent( this, LogViewer.class );
		it.putExtra( LogViewer.DMESG_MODE, dmesg );

		startActivityForResult( it, 1 );
	}

	void sendReport( final boolean[] items, final int format,
			final boolean compressed )
	{
		if ( progress != null )
		{
			progress.dismiss( );
		}
		progress = new ProgressDialog( this );
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
					content = Util.createCompressedContent( handler,
							SysInfoManager.this,
							content,
							format,
							"android_report" ); //$NON-NLS-1$
				}

				if ( aborted )
				{
					return;
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

	String generateTextReport( boolean[] items )
	{
		StringBuffer sb = new StringBuffer( );

		createTextHeader( this, sb, "Android System Report - " //$NON-NLS-1$
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
					.append( getString( R.string.a2sd_storage ) )
					.append( "\n\t" ); //$NON-NLS-1$

			info = getA2SDStorageInfo( );
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
					.append( getString( R.string.system_storage ) )
					.append( "\n\t" ); //$NON-NLS-1$

			info = getSystemStorageInfo( );
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
					.append( getString( R.string.cache_storage ) )
					.append( "\n\t" ); //$NON-NLS-1$

			info = getCacheStorageInfo( );
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
						info[2] ) + getString( R.string.idle_info, info[1] ) );
			}
			sb.append( "\n\n" ); //$NON-NLS-1$

			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.processor ) )
					.append( "\n\t" ) //$NON-NLS-1$
					.append( getCpuInfo( ) )
					.append( "\n\n" ); //$NON-NLS-1$

			String nInfo = getNetAddressInfo( );
			sb.append( "* " ) //$NON-NLS-1$
					.append( getString( R.string.net_address ) )
					.append( "\n\t" ) //$NON-NLS-1$
					.append( nInfo == null ? getString( R.string.info_not_available )
							: nInfo )
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

				f = new File( F_MOUNT_INFO );
				if ( f.exists( ) )
				{
					readRawText( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( getString( R.string.no_mount_info ) )
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
				for ( int i = 0, size = pkgs.size( ); i < size; i++ )
				{
					PackageInfo pkg = pkgs.get( i );

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

				for ( int i = 0, size = procs.size( ); i < size; i++ )
				{
					RunningAppProcessInfo proc = procs.get( i );

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

	String generateHtmlReport( boolean[] items )
	{
		StringBuffer sb = new StringBuffer( );

		createHtmlHeader( this,
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
					.append( getString( R.string.a2sd_storage ) )
					.append( nextColumn4 );

			info = getA2SDStorageInfo( );
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
					.append( getString( R.string.system_storage ) )
					.append( nextColumn4 );

			info = getSystemStorageInfo( );
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
					.append( getString( R.string.cache_storage ) )
					.append( nextColumn4 );

			info = getCacheStorageInfo( );
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
						info[2] ) + getString( R.string.idle_info, info[1] ) );
			}
			sb.append( closeRow );

			sb.append( openRow )
					.append( getString( R.string.processor ) )
					.append( nextColumn4 )
					.append( escapeHtml( getCpuInfo( ) ) )
					.append( closeRow );

			String nInfo = getNetAddressInfo( );
			sb.append( openRow )
					.append( getString( R.string.net_address ) )
					.append( nextColumn4 )
					.append( nInfo == null ? getString( R.string.info_not_available )
							: nInfo )
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

				f = new File( F_MOUNT_INFO );
				if ( f.exists( ) )
				{
					readRawHTML( sb, new FileInputStream( f ) );
				}
				else
				{
					sb.append( openFullRow )
							.append( getString( R.string.no_mount_info ) )
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
				for ( int i = 0, size = pkgs.size( ); i < size; i++ )
				{
					PackageInfo pkg = pkgs.get( i );

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

				for ( int i = 0, size = procs.size( ); i < size; i++ )
				{
					RunningAppProcessInfo proc = procs.get( i );

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
			case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE :
				impt = "Perceptible"; //$NON-NLS-1$
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

	static void readRawText( StringBuffer sb, InputStream input )
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

	static void readRawHTML( StringBuffer sb, InputStream input )
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

	static String getVersionName( PackageManager pm, String pkgName )
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

	static void createTextHeader( Context context, StringBuffer sb, String title )
	{
		sb.append( title ).append( "\n\n" ); //$NON-NLS-1$

		sb.append( context.getString( R.string.collector_head,
				context.getString( R.string.app_name ),
				SysInfoManager.getVersionName( context.getPackageManager( ),
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
			SysInfoManager.readRawText( sb,
					new FileInputStream( SysInfoManager.F_VERSION ) );

			sb.append( '\n' );
		}
		catch ( Exception e )
		{
			Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
		}
	}

	static void createHtmlHeader( Context context, StringBuffer sb, String title )
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
		sb.append( SysInfoManager.escapeHtml( context.getString( R.string.collector_head,
				context.getString( R.string.app_name ),
				SysInfoManager.getVersionName( context.getPackageManager( ),
						context.getPackageName( ) ) ) ) );
		sb.append( "</small></font></td></tr>\n" ); //$NON-NLS-1$

		sb.append( SysInfoManager.openHeaderRow )
				.append( context.getString( R.string.device_info ) )
				.append( SysInfoManager.closeHeaderRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.device ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.DEVICE ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.model ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.MODEL ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.product ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.PRODUCT ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.brand ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.BRAND ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.release ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.VERSION.RELEASE ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.build ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Build.DISPLAY ) )
				.append( SysInfoManager.closeRow );
		sb.append( SysInfoManager.openRow )
				.append( context.getString( R.string.locale ) )
				.append( SysInfoManager.nextColumn4 )
				.append( SysInfoManager.escapeHtml( Locale.getDefault( )
						.toString( ) ) )
				.append( SysInfoManager.closeRow );

		sb.append( SysInfoManager.emptyRow );

		sb.append( SysInfoManager.openHeaderRow )
				.append( context.getString( R.string.sys_version ) )
				.append( SysInfoManager.closeHeaderRow );

		try
		{
			SysInfoManager.readRawHTML( sb,
					new FileInputStream( SysInfoManager.F_VERSION ) );

			sb.append( SysInfoManager.emptyRow );
		}
		catch ( Exception e )
		{
			Log.e( LogViewer.class.getName( ), e.getLocalizedMessage( ), e );
		}
	}

	static String escapeCsv( String str )
	{
		if ( TextUtils.isEmpty( str ) || containsNone( str, CSV_SEARCH_CHARS ) )
		{
			return str;
		}

		StringBuffer sb = new StringBuffer( );

		sb.append( '"' );
		for ( int i = 0, size = str.length( ); i < size; i++ )
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

	static String escapeHtml( String str )
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
		for ( int i = 0, size = str.length( ); i < size; i++ )
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

	static int[] getWidgetIds( String names )
	{
		if ( names != null )
		{
			String[] ss = names.split( "," ); //$NON-NLS-1$

			if ( ss != null && ss.length > 0 )
			{
				int[] id = new int[ss.length];
				int idx = 0;

				for ( String s : ss )
				{
					if ( s.equals( WidgetProvider.class.getSimpleName( ) ) )
					{
						id[idx] = WIDGET_BAR;
					}
					else if ( s.equals( InfoWidget.class.getSimpleName( ) ) )
					{
						id[idx] = WIDGET_INFO;
					}
					else if ( s.equals( TaskWidget.class.getSimpleName( ) ) )
					{
						id[idx] = WIDGET_TASK;
					}

					idx++;
				}

				return id;
			}
		}

		return null;
	}

	static String getWidgetName( int id )
	{
		Class<?> clz = getWidgetClass( id );

		if ( clz != null )
		{
			return clz.getSimpleName( );
		}

		return null;
	}

	static Class<?> getWidgetClass( int id )
	{
		switch ( id )
		{
			case WIDGET_BAR :
				return WidgetProvider.class;
			case WIDGET_INFO :
				return InfoWidget.class;
			case WIDGET_TASK :
				return TaskWidget.class;
		}

		return null;
	}

	/**
	 * FormatItem
	 */
	static final class FormatItem
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
	static final class FormatArrayAdapter extends ArrayAdapter<FormatItem>
	{

		Activity context;

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

	/**
	 * PopActivity
	 */
	static abstract class PopActivity extends Activity
	{

		private GestureDetector gestureDetector;

		protected boolean eventConsumed;

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			super.onCreate( savedInstanceState );

			requestWindowFeature( Window.FEATURE_NO_TITLE );

			setContentView( R.layout.pop_view );

			gestureDetector = new GestureDetector( this,
					new GestureDetector.SimpleOnGestureListener( ) {

						@Override
						public boolean onSingleTapConfirmed( MotionEvent e )
						{
							if ( eventConsumed )
							{
								eventConsumed = false;
							}
							else
							{
								finish( );
							}
							return true;
						}
					} );
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
			View view = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).targetView;

			if ( view != null )
			{
				TextView txtHead = (TextView) view.findViewById( R.id.txt_head );
				TextView txtMsg = (TextView) view.findViewById( R.id.txt_msg );

				String s = null;

				if ( txtHead != null )
				{
					s = txtHead.getText( ).toString( );
				}

				if ( txtMsg != null )
				{
					if ( s != null )
					{
						s += '\n' + txtMsg.getText( ).toString( );
					}
					else
					{
						s = txtMsg.getText( ).toString( );
					}
				}

				ClipboardManager cm = (ClipboardManager) getSystemService( CLIPBOARD_SERVICE );

				if ( cm != null && !TextUtils.isEmpty( s ) )
				{
					cm.setText( s );

					Util.shortToast( this, R.string.copied_hint );
				}
			}

			return true;
		}

		@Override
		public boolean dispatchTouchEvent( MotionEvent ev )
		{
			if ( gestureDetector.onTouchEvent( ev ) )
			{
				return true;
			}

			return super.dispatchTouchEvent( ev );
		}
	}

	/**
	 * InfoSettings
	 */
	public static final class InfoSettings extends PreferenceActivity
	{

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			requestWindowFeature( Window.FEATURE_NO_TITLE );

			super.onCreate( savedInstanceState );

			setPreferenceScreen( getPreferenceManager( ).createPreferenceScreen( this ) );

			PreferenceCategory pc = new PreferenceCategory( this );
			pc.setTitle( R.string.preference );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfTab = new Preference( this );
			perfTab.setKey( PREF_KEY_DEFAULT_TAB );
			perfTab.setTitle( R.string.default_tab );
			pc.addPreference( perfTab );

			Preference perfEmail = new Preference( this );
			perfEmail.setKey( PREF_KEY_DEFAULT_EMAIL );
			perfEmail.setTitle( R.string.default_email );
			pc.addPreference( perfEmail );

			Preference perfWidget = new Preference( this );
			perfWidget.setKey( PREF_KEY_WIDGET_DISABLED );
			perfWidget.setTitle( R.string.configure_widgets );
			perfWidget.setSummary( R.string.configure_widgets_sum );
			pc.addPreference( perfWidget );

			pc = new PreferenceCategory( this );
			pc.setTitle( R.string.notifications );
			getPreferenceScreen( ).addPreference( pc );

			CheckBoxPreference prefInfo = new CheckBoxPreference( this );
			prefInfo.setKey( PREF_KEY_SHOW_INFO_ICON );
			prefInfo.setTitle( R.string.show_info_icon );
			prefInfo.setSummary( R.string.show_info_icon_sum );
			pc.addPreference( prefInfo );

			CheckBoxPreference prefTask = new CheckBoxPreference( this );
			prefTask.setKey( PREF_KEY_SHOW_TASK_ICON );
			prefTask.setTitle( R.string.show_task_icon );
			prefTask.setSummary( R.string.show_task_icon_sum );
			pc.addPreference( prefTask );

			CheckBoxPreference prefAuto = new CheckBoxPreference( this );
			prefAuto.setKey( PREF_KEY_AUTO_START_ICON );
			prefAuto.setTitle( R.string.auto_start );
			prefAuto.setSummary( R.string.auto_start_sum );
			pc.addPreference( prefAuto );

			refreshEmail( );
			refreshTab( );
			refreshBooleanOption( PREF_KEY_SHOW_INFO_ICON, true );
			refreshBooleanOption( PREF_KEY_SHOW_TASK_ICON, true );
			refreshBooleanOption( PREF_KEY_AUTO_START_ICON, false );

			setResult( RESULT_OK, getIntent( ) );
		}

		void refreshBooleanOption( String key, boolean defValue )
		{
			boolean val = getIntent( ).getBooleanExtra( key, defValue );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
		}

		void refreshEmail( )
		{
			String email = getIntent( ).getStringExtra( PREF_KEY_DEFAULT_EMAIL );

			if ( email == null )
			{
				findPreference( PREF_KEY_DEFAULT_EMAIL ).setSummary( R.string.none );
			}
			else
			{
				findPreference( PREF_KEY_DEFAULT_EMAIL ).setSummary( email );
			}
		}

		void refreshTab( )
		{
			int tab = getIntent( ).getIntExtra( PREF_KEY_DEFAULT_TAB, 0 );

			CharSequence label = getString( R.string.last_active );
			switch ( tab )
			{
				case 1 :
					label = getString( R.string.tab_info );
					break;
				case 2 :
					label = getString( R.string.tab_apps );
					break;
				case 3 :
					label = getString( R.string.tab_procs );
					break;
				case 4 :
					label = getString( R.string.tab_netstat );
					break;
			}

			findPreference( PREF_KEY_DEFAULT_TAB ).setSummary( label );
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( PREF_KEY_SHOW_INFO_ICON.equals( preference.getKey( ) ) )
			{
				boolean enabled = ( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_INFO_ICON ) ).isChecked( );

				it.putExtra( PREF_KEY_SHOW_INFO_ICON, enabled );

				Util.updateInfoIcon( this, enabled );

				return true;
			}
			else if ( PREF_KEY_SHOW_TASK_ICON.equals( preference.getKey( ) ) )
			{
				boolean enabled = ( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_TASK_ICON ) ).isChecked( );

				it.putExtra( PREF_KEY_SHOW_TASK_ICON, enabled );

				Util.updateTaskIcon( this, enabled );

				return true;
			}
			else if ( PREF_KEY_AUTO_START_ICON.equals( preference.getKey( ) ) )
			{
				it.putExtra( PREF_KEY_AUTO_START_ICON,
						( (CheckBoxPreference) findPreference( PREF_KEY_AUTO_START_ICON ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_DEFAULT_EMAIL.equals( preference.getKey( ) ) )
			{
				final EditText txt = new EditText( this );
				txt.setText( it.getStringExtra( PREF_KEY_DEFAULT_EMAIL ) );

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						String email = txt.getText( ).toString( );

						if ( email != null )
						{
							email = email.trim( );

							if ( email.length( ) == 0 )
							{
								email = null;
							}
						}

						it.putExtra( PREF_KEY_DEFAULT_EMAIL, email );

						dialog.dismiss( );

						refreshEmail( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.default_email )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setView( txt )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_DEFAULT_TAB.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_DEFAULT_TAB, which );

						dialog.dismiss( );

						refreshTab( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.default_tab )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getString( R.string.last_active ),
								getString( R.string.tab_info ),
								getString( R.string.tab_apps ),
								getString( R.string.tab_procs ),
								getString( R.string.tab_netstat )
						},
								it.getIntExtra( PREF_KEY_DEFAULT_TAB, 0 ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_WIDGET_DISABLED.equals( preference.getKey( ) ) )
			{
				final boolean[] states = new boolean[4];
				Arrays.fill( states, true );

				String disabled = it.getStringExtra( PREF_KEY_WIDGET_DISABLED );

				if ( disabled != null )
				{
					int[] ids = getWidgetIds( disabled );

					if ( ids != null )
					{
						for ( int i : ids )
						{
							states[i] = false;
						}
					}
				}

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						PackageManager pm = getPackageManager( );

						StringBuilder disabled = new StringBuilder( );

						int idx = 0;
						for ( boolean b : states )
						{
							// record disabled
							if ( !b )
							{
								String name = getWidgetName( idx );

								if ( name != null )
								{
									if ( disabled.length( ) > 0 )
									{
										disabled.append( ',' );
									}

									disabled.append( name );
								}
							}

							// refresh widget enablement
							Class<?> clz = getWidgetClass( idx );

							if ( clz != null )
							{
								ComponentName comp = new ComponentName( InfoSettings.this,
										clz );

								int setting = pm.getComponentEnabledSetting( comp );

								if ( b
										&& setting != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT )
								{
									pm.setComponentEnabledSetting( comp,
											PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
											PackageManager.DONT_KILL_APP );
								}
								else if ( !b
										&& setting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED )
								{
									pm.setComponentEnabledSetting( comp,
											PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
											PackageManager.DONT_KILL_APP );
								}
							}

							idx++;
						}

						String names = disabled.length( ) > 0 ? disabled.toString( )
								: null;

						it.putExtra( PREF_KEY_WIDGET_DISABLED, names );

						dialog.dismiss( );

						Util.longToast( InfoSettings.this,
								R.string.reboot_warning );
					}
				};

				OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener( ) {

					public void onClick( DialogInterface dialog, int which,
							boolean isChecked )
					{
						states[which] = isChecked;
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.widgets )
						.setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null )
						.setMultiChoiceItems( new CharSequence[]{
								getString( R.string.widget_bar_name ),
								getString( R.string.app_name ),
								getString( R.string.task_widget_name ),
						},
								states,
								multiListener )
						.create( )
						.show( );

				return true;
			}

			return false;
		}
	}
}
