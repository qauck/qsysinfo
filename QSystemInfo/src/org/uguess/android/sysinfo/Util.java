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

import java.lang.reflect.Field;

import org.uguess.android.sysinfo.WidgetProvider.EndTaskService;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

/**
 * Util
 */
final class Util
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

	static boolean updateIntOption( Intent data, Activity ac, String key,
			int defValue )
	{
		int t = data.getIntExtra( key, defValue );
		if ( t != getIntOption( ac, key, defValue ) )
		{
			setIntOption( ac, key, t );
			return true;
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
		boolean b = data.getBooleanExtra( key, defValue );
		if ( b != getBooleanOption( ac, key, defValue ) )
		{
			setBooleanOption( ac, key, b );
			return true;
		}
		return false;
	}

	static boolean updateStringOption( Intent data, Activity ac, String key )
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
					ctx.getString( R.string.app_name ),
					System.currentTimeMillis( ) );

			nc.flags = Notification.FLAG_NO_CLEAR;
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
					ctx.getString( R.string.task_widget_name ),
					System.currentTimeMillis( ) );

			nc.flags = Notification.FLAG_NO_CLEAR;
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

}
