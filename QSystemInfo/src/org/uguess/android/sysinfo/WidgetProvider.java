/********************************************************************************
 * (C) Copyright 2000-2010, by Shawn Qualia.
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
import java.util.List;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;

/**
 * QSystemInfoWidgetProvider
 */
public final class WidgetProvider extends AppWidgetProvider
{

	@Override
	public void onUpdate( Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds )
	{
		update( context, appWidgetManager, appWidgetIds, true, true );
	}

	private static void update( Context context,
			AppWidgetManager appWidgetManager, int[] appWidgetIds,
			boolean hasTask, boolean hasInfo )
	{
		if ( appWidgetIds != null )
		{
			for ( int id : appWidgetIds )
			{
				RemoteViews rv = new RemoteViews( context.getPackageName( ),
						R.layout.widget );

				if ( hasTask )
				{
					Intent it = new Intent( context, EndTaskService.class );

					PendingIntent pi = PendingIntent.getService( context,
							0,
							it,
							0 );

					rv.setOnClickPendingIntent( R.id.img_kill, pi );
				}
				else
				{
					rv.setViewVisibility( R.id.img_kill, View.GONE );
				}

				if ( hasInfo )
				{
					Intent it = new Intent( context, QSystemInfo.class );
					it.setFlags( it.getFlags( )
							| Intent.FLAG_ACTIVITY_NEW_TASK
							| Intent.FLAG_ACTIVITY_CLEAR_TOP );

					PendingIntent pi = PendingIntent.getActivity( context,
							0,
							it,
							0 );

					rv.setOnClickPendingIntent( R.id.img_info, pi );
				}
				else
				{
					rv.setViewVisibility( R.id.img_info, View.GONE );
				}

				appWidgetManager.updateAppWidget( id, rv );
			}
		}
	}

	/**
	 * TaskWidget
	 */
	public static final class TaskWidget extends AppWidgetProvider
	{

		@Override
		public void onUpdate( Context context,
				AppWidgetManager appWidgetManager, int[] appWidgetIds )
		{
			update( context, appWidgetManager, appWidgetIds, true, false );
		}
	}

	/**
	 * InfoWidget
	 */
	public static final class InfoWidget extends AppWidgetProvider
	{

		@Override
		public void onUpdate( Context context,
				AppWidgetManager appWidgetManager, int[] appWidgetIds )
		{
			update( context, appWidgetManager, appWidgetIds, false, true );
		}
	}

	/**
	 * EndTaskService
	 */
	public static final class EndTaskService extends Service
	{

		@Override
		public IBinder onBind( Intent intent )
		{
			return null;
		}

		@Override
		public void onStart( Intent intent, int startId )
		{
			ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

			List<RunningAppProcessInfo> raps = am.getRunningAppProcesses( );

			if ( raps == null )
			{
				return;
			}

			String self = getPackageName( );
			int killed = 0;
			int ignored = 0;
			String name;

			ArrayList<String> ignoreList = ProcessManager.getIgnoreList( getSharedPreferences( ProcessManager.class.getSimpleName( ),
					Context.MODE_PRIVATE ) );

			for ( RunningAppProcessInfo rap : raps )
			{
				name = rap.processName;

				if ( name.equals( self )
						|| name.startsWith( "com.google.process" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.phone" ) //$NON-NLS-1$
						|| name.startsWith( "android.process" ) //$NON-NLS-1$
						|| name.startsWith( "system" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.inputmethod" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.alarmclock" ) ) //$NON-NLS-1$
				{
					continue;
				}

				if ( ignoreList != null && ignoreList.contains( name ) )
				{
					ignored++;
				}
				else if ( rap.pkgList != null )
				{
					for ( String pkg : rap.pkgList )
					{
						if ( pkg != null )
						{
							am.restartPackage( pkg );
							killed++;
						}
					}
				}
			}

			Util.shortToast( this, getString( killed > 1 ? R.string.kill_info2
					: R.string.kill_info, killed, ignored ) );
		}
	}
}
