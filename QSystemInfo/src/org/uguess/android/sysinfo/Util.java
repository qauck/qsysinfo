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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.uguess.android.sysinfo.QSystemInfo.ErrorHandler;
import org.uguess.android.sysinfo.WidgetProvider.EndTaskService;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Util
 */
final class Util implements Constants
{

	private static Field fdTargetSdkVersion = null;

	static
	{
		try
		{
			fdTargetSdkVersion = ApplicationInfo.class.getDeclaredField( "targetSdkVersion" ); //$NON-NLS-1$
		}
		catch ( Exception e )
		{
			Log.d( Util.class.getName( ),
					"Current SDK version do not support 'targetSdkVersion' property." ); //$NON-NLS-1$
		}
	}

	static String getTargetSdkVersion( Context ctx, ApplicationInfo ai )
	{
		if ( fdTargetSdkVersion != null )
		{
			try
			{
				return String.valueOf( fdTargetSdkVersion.get( ai ) );
			}
			catch ( Exception e )
			{
				Log.e( Util.class.getName( ), e.getLocalizedMessage( ), e );
			}
		}

		return ctx.getString( R.string.unknown );
	}

	static void killSelf( Handler handler, Activity ac, ActivityManager am,
			String pkgName )
	{
		int sdkInt = 0;

		try
		{
			sdkInt = Integer.parseInt( Build.VERSION.SDK );
		}
		catch ( Exception e )
		{
			Log.e( Util.class.getName( ), e.getLocalizedMessage( ), e );
		}

		if ( sdkInt < 8 )
		{
			ac.finish( );
			am.restartPackage( pkgName );
		}
		else
		{
			ac.finish( );

			updateInfoIcon( ac, false );
			updateTaskIcon( ac, false );

			handler.postDelayed( new Runnable( ) {

				public void run( )
				{
					Process.killProcess( Process.myPid( ) );
				}
			}, 500 );
		}
	}

	/**
	 * @return -1->sys ignored, 1->user ignored, 0->killable
	 */
	static int killable( String pkgName, String self,
			Collection<String> ignoreList )
	{
		if ( pkgName.equals( self ) || isSysProcess( pkgName ) )
		{
			return -1;
		}

		if ( ignoreList != null && ignoreList.contains( pkgName ) )
		{
			return 1;
		}

		return 0;
	}

	static boolean isSysProcess( String pkgName )
	{
		return pkgName.startsWith( "com.google.process" ) //$NON-NLS-1$
				|| pkgName.startsWith( "com.android.phone" ) //$NON-NLS-1$
				|| pkgName.startsWith( "android.process" ) //$NON-NLS-1$
				|| pkgName.startsWith( "system" ) //$NON-NLS-1$
				|| pkgName.startsWith( "com.android.inputmethod" ) //$NON-NLS-1$
				|| pkgName.startsWith( "com.android.alarmclock" ); //$NON-NLS-1$
	}

	static int getIntOption( Activity ac, String key, int defValue )
	{
		return ac.getPreferences( Context.MODE_PRIVATE ).getInt( key, defValue );
	}

	private static void setIntOption( Activity ac, String key, int val )
	{
		Editor et = ac.getPreferences( Context.MODE_PRIVATE ).edit( );
		et.putInt( key, val );
		et.commit( );
	}

	static boolean getBooleanOption( Activity ac, String key )
	{
		return getBooleanOption( ac, key, true );
	}

	static boolean getBooleanOption( Activity ac, String key, boolean defValue )
	{
		return ac.getPreferences( Context.MODE_PRIVATE ).getBoolean( key,
				defValue );
	}

	private static void setBooleanOption( Activity ac, String key, boolean val )
	{
		Editor et = ac.getPreferences( Context.MODE_PRIVATE ).edit( );
		et.putBoolean( key, val );
		et.commit( );
	}

	static String getStringOption( Activity ac, String key, String defValue )
	{
		SharedPreferences sp = ac.getPreferences( Context.MODE_PRIVATE );

		return sp.getString( key, defValue );
	}

	private static void setStringOption( Activity ac, String key, String val )
	{
		SharedPreferences sp = ac.getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		if ( val == null )
		{
			et.remove( key );
		}
		else
		{
			et.putString( key, val );
		}
		et.commit( );
	}

	static void shortToast( Context context, int resId )
	{
		Toast.makeText( context, resId, Toast.LENGTH_SHORT ).show( );
	}

	static void shortToast( Context context, String msg )
	{
		Toast.makeText( context, msg, Toast.LENGTH_SHORT ).show( );
	}

	static void longToast( Context context, int resId )
	{
		Toast.makeText( context, resId, Toast.LENGTH_LONG ).show( );
	}

	static void longToast( Context context, String msg )
	{
		Toast.makeText( context, msg, Toast.LENGTH_LONG ).show( );
	}

	static boolean updateIntOption( Intent data, Activity ac, String key,
			int defValue )
	{
		if ( data != null )
		{
			int t = data.getIntExtra( key, defValue );
			if ( t != getIntOption( ac, key, defValue ) )
			{
				setIntOption( ac, key, t );
				return true;
			}
		}
		return false;
	}

	static boolean updateBooleanOption( Intent data, Activity ac, String key )
	{
		return updateBooleanOption( data, ac, key, true );
	}

	static boolean updateBooleanOption( Intent data, Activity ac, String key,
			boolean defValue )
	{
		if ( data != null )
		{
			boolean b = data.getBooleanExtra( key, defValue );
			if ( b != getBooleanOption( ac, key, defValue ) )
			{
				setBooleanOption( ac, key, b );
				return true;
			}
		}
		return false;
	}

	static boolean updateStringOption( Intent data, Activity ac, String key )
	{
		if ( data != null )
		{
			String s = data.getStringExtra( key );

			if ( s != null )
			{
				s = s.trim( );

				if ( s.length( ) == 0 )
				{
					s = null;
				}
			}

			if ( !TextUtils.equals( s, getStringOption( ac, key, null ) ) )
			{
				setStringOption( ac, key, s );
				return true;
			}
		}
		return false;
	}

	static void updateIcons( Context ctx, SharedPreferences sp )
	{
		if ( sp == null )
		{
			return;
		}

		if ( sp.getBoolean( SysInfoManager.PREF_KEY_SHOW_INFO_ICON, true ) )
		{
			updateInfoIcon( ctx, true );
		}

		if ( sp.getBoolean( SysInfoManager.PREF_KEY_SHOW_TASK_ICON, true ) )
		{
			updateTaskIcon( ctx, true );
		}
	}

	static void updateInfoIcon( Context ctx, boolean enable )
	{
		if ( enable )
		{
			Intent it = new Intent( ctx, QSystemInfo.class );
			it.setFlags( it.getFlags( )
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TOP );

			PendingIntent pi = PendingIntent.getActivity( ctx, 0, it, 0 );

			Notification nc = new Notification( R.drawable.icon,
					null,
					System.currentTimeMillis( ) );

			nc.flags = Notification.FLAG_NO_CLEAR
					| Notification.FLAG_ONGOING_EVENT;
			nc.setLatestEventInfo( ctx,
					ctx.getString( R.string.app_name ),
					ctx.getString( R.string.info_icon_hint ),
					pi );

			( (NotificationManager) ctx.getSystemService( Context.NOTIFICATION_SERVICE ) ).notify( Integer.MAX_VALUE,
					nc );
		}
		else
		{
			( (NotificationManager) ctx.getSystemService( Context.NOTIFICATION_SERVICE ) ).cancel( Integer.MAX_VALUE );
		}
	}

	static void updateTaskIcon( Context ctx, boolean enable )
	{
		if ( enable )
		{
			Intent it = new Intent( ctx, EndTaskService.class );

			PendingIntent pi = PendingIntent.getService( ctx, 0, it, 0 );

			Notification nc = new Notification( R.drawable.end,
					null,
					System.currentTimeMillis( ) );

			nc.flags = Notification.FLAG_NO_CLEAR
					| Notification.FLAG_ONGOING_EVENT;
			nc.setLatestEventInfo( ctx,
					ctx.getString( R.string.task_widget_name ),
					ctx.getString( R.string.task_icon_hint ),
					pi );

			( (NotificationManager) ctx.getSystemService( Context.NOTIFICATION_SERVICE ) ).notify( Integer.MAX_VALUE - 1,
					nc );
		}
		else
		{
			( (NotificationManager) ctx.getSystemService( Context.NOTIFICATION_SERVICE ) ).cancel( Integer.MAX_VALUE - 1 );
		}
	}

	private static void sendContent( Activity context, String email,
			String subject, String content, boolean compressed )
	{
		Intent it = new Intent( Intent.ACTION_SEND );

		it.putExtra( Intent.EXTRA_SUBJECT, subject );

		if ( email != null )
		{
			it.putExtra( Intent.EXTRA_EMAIL, new String[]{
				email
			} );
		}

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

	static void handleMsgSendContentReady( String content, String prefix,
			Activity ctx, boolean compressed )
	{
		if ( content == null )
		{
			Util.shortToast( ctx, R.string.no_content_sent );
		}
		else
		{
			SharedPreferences sp = ctx.getSharedPreferences( SysInfoManager.class.getSimpleName( ),
					Context.MODE_PRIVATE );

			String email = null;

			if ( sp != null )
			{
				email = sp.getString( SysInfoManager.PREF_KEY_DEFAULT_EMAIL,
						null );
			}

			sendContent( ctx,
					email,
					prefix + new Date( ).toLocaleString( ),
					content,
					compressed );
		}
	}

	static void checkForceCompression( final Handler handler,
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
					String sendContent = createCompressedContent( null,
							context,
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

	static String createCompressedContent( Handler handler, Activity context,
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
					if ( handler == null )
					{
						Util.shortToast( context,
								context.getString( R.string.error_create_folder,
										tf.getAbsolutePath( ) ) );
					}
					else
					{
						handler.sendMessage( handler.obtainMessage( MSG_TOAST,
								context.getString( R.string.error_create_folder,
										tf.getAbsolutePath( ) ) ) );
					}

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
			if ( handler == null )
			{
				Util.shortToast( context, R.string.error_sdcard );
			}
			else
			{
				handler.sendMessage( handler.obtainMessage( MSG_TOAST,
						context.getString( R.string.error_sdcard ) ) );
			}
		}

		return null;
	}

	static void hookExceptionHandler( Context ctx )
	{
		UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler( );

		if ( !( oldHandler instanceof ErrorHandler ) )
		{
			Thread.setDefaultUncaughtExceptionHandler( new ErrorHandler( ctx,
					oldHandler ) );
		}
	}

	static Intent getSettingsIntent( PackageManager pm, String clzName )
	{
		Intent it = new Intent( Intent.ACTION_MAIN );
		it.setClassName( "com.android.settings", clzName ); //$NON-NLS-1$

		List<ResolveInfo> acts = pm.queryIntentActivities( it, 0 );

		if ( acts.size( ) > 0 )
		{
			return it;
		}

		return null;
	}
}
