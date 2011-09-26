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

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
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

	static void update( Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds, boolean hasTask, boolean hasInfo )
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

		private Handler handler = new Handler( );

		@Override
		public void onCreate( )
		{
			Util.hookExceptionHandler( getApplicationContext( ) );
		}

		@Override
		public IBinder onBind( Intent intent )
		{
			return null;
		}

		@Override
		public void onStart( Intent intent, final int startId )
		{
			final ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

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

			final long oldMem = getAvailableMem( am );

			for ( int i = 0, size = raps.size( ); i < size; i++ )
			{
				RunningAppProcessInfo rap = raps.get( i );

				name = rap.processName;

				int killType = Util.killable( name, self, ignoreList );

				if ( killType == -1 )
				{
					continue;
				}

				if ( killType == 1 )
				{
					ignored++;
				}
				else if ( rap.pkgList != null )
				{
					for ( String pkg : rap.pkgList )
					{
						if ( pkg != null )
						{
							int subKillType = Util.killable( pkg,
									self,
									ignoreList );

							if ( subKillType == 0 )
							{
								am.restartPackage( pkg );
								killed++;
							}
						}
					}
				}
			}

			final int bKilled = killed;
			final int bIgnored = ignored;

			handler.postDelayed( new Runnable( ) {

				public void run( )
				{
					long gain = 0;

					if ( oldMem != -1 )
					{
						long newMem = getAvailableMem( am );

						if ( newMem != -1 )
						{
							gain = newMem - oldMem;
						}

						if ( gain < 0 )
						{
							gain = 0;
						}
					}

					Util.shortToast( EndTaskService.this,
							getString( bKilled > 1 ? R.string.kill_info2
									: R.string.kill_info,
									bKilled,
									bIgnored,
									Formatter.formatFileSize( EndTaskService.this,
											gain ) ) );

					stopSelfResult( startId );
				}
			},
					300 );
		}

		static long getAvailableMem( ActivityManager am )
		{
			long mem = -1;

			try
			{
				MemoryInfo mi = new MemoryInfo( );
				am.getMemoryInfo( mi );
				mem = mi.availMem;
			}
			catch ( Exception e )
			{
				Log.d( EndTaskService.class.getName( ),
						e.getLocalizedMessage( ),
						e );
			}

			return mem;
		}
	}
}
