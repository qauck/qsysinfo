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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringTokenizer;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * ProcessManager
 */
public final class ProcessManager extends ListActivity
{

	private static final int MI_DISPLAY = 1;
	private static final int MI_ENDTASK = 2;
	private static final int MI_IGNORE = 3;
	private static final int MI_DETAIL = 4;

	private static final int MSG_UPDATE = 1;

	private static final String PREF_KEY_REFRESH_INTERVAL = "refresh_interval"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_ORDER_TYPE = "sort_order_type"; //$NON-NLS-1$
	private static final String PREF_KEY_SORT_DIRECTION = "sort_direction"; //$NON-NLS-1$
	private static final String PREF_KEY_IGNORE_ACTION = "ignore_action"; //$NON-NLS-1$
	private static final String PREF_KEY_IGNORE_LIST = "ignore_list"; //$NON-NLS-1$

	private static final int REFRESH_HIGH = 0;
	private static final int REFRESH_NORMAL = 1;
	private static final int REFRESH_LOW = 2;
	private static final int REFRESH_PAUSED = 3;

	private static final int ORDER_TYPE_NAME = 0;
	private static final int ORDER_TYPE_IMPORTANCE = 1;
	private static final int ORDER_TYPE_MEM = 2;
	private static final int ORDER_TYPE_CPU = 3;

	private static final int IGNORE_ACTION_HIDDEN = 0;
	private static final int IGNORE_ACTION_PROTECTED = 1;

	private static final int ORDER_ASC = 1;
	private static final int ORDER_DESC = -1;

	private ProcessItem dummyInfo;

	private ProcessCache procCache;

	private long totalLoad, totalDelta;

	private LinkedHashSet<String> ignoreList;

	private Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			if ( msg.what == MSG_UPDATE )
			{
				ArrayAdapter<ProcessItem> adapter = (ArrayAdapter<ProcessItem>) getListView( ).getAdapter( );

				adapter.setNotifyOnChange( false );

				adapter.clear( );

				adapter.add( dummyInfo );

				for ( ProcessItem pi : procCache.procList )
				{
					adapter.add( pi );
				}

				adapter.notifyDataSetChanged( );

				int interval = getRefreshInterval( );

				switch ( interval )
				{
					case REFRESH_HIGH :
						handler.postDelayed( task, 1000 );
						break;
					case REFRESH_NORMAL :
						handler.postDelayed( task, 2000 );
						break;
					case REFRESH_LOW :
						handler.postDelayed( task, 4000 );
						break;
				}
			}
		};
	};

	private Runnable task = new Runnable( ) {

		public void run( )
		{
			ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

			List<RunningAppProcessInfo> raps = am.getRunningAppProcesses( );

			updateProcess( raps );

			handler.sendEmptyMessage( MSG_UPDATE );
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		registerForContextMenu( getListView( ) );

		dummyInfo = new ProcessItem( );
		dummyInfo.label = ProcessManager.this.getString( R.string.end_proc_hint );

		procCache = new ProcessCache( );

		ignoreList = new LinkedHashSet<String>( );

		ArrayList<String> list = getIgnoreList( );

		if ( list != null )
		{
			ignoreList.addAll( list );
		}

		getListView( ).setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				ProcessItem rap = (ProcessItem) parent.getItemAtPosition( position );

				if ( rap == dummyInfo )
				{
					endAll( );
				}
				else if ( !ignoreList.contains( rap.procInfo.processName ) )
				{
					ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

					String self = getPackageName( );

					if ( self.equals( rap.procInfo.processName ) )
					{
						am.restartPackage( self );
					}
					else
					{
						endProcess( am, rap.procInfo.pkgList );

						handler.removeCallbacks( task );
						handler.post( task );
					}
				}
			}
		} );

		ArrayAdapter<ProcessItem> adapter = new ArrayAdapter<ProcessItem>( this,
				R.layout.proc_item ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name, txt_mem, txt_cpu;
				ImageView img_type;

				if ( convertView == null )
				{
					view = ProcessManager.this.getLayoutInflater( )
							.inflate( R.layout.proc_item, parent, false );
				}
				else
				{
					view = convertView;
				}

				ProcessItem itm = getItem( position );

				img_type = (ImageView) view.findViewById( R.id.img_proc_icon );
				txt_name = (TextView) view.findViewById( R.id.txt_proc_name );
				txt_mem = (TextView) view.findViewById( R.id.txt_mem );
				txt_cpu = (TextView) view.findViewById( R.id.txt_cpu );

				if ( itm == dummyInfo )
				{
					txt_name.setText( itm.label );
					txt_name.setTypeface( Typeface.DEFAULT, Typeface.ITALIC );
					txt_name.setTextColor( Color.WHITE );

					img_type.setImageDrawable( null );

					txt_mem.setText( "MEM" ); //$NON-NLS-1$
					txt_cpu.setText( "CPU%" ); //$NON-NLS-1$
				}
				else
				{
					txt_name.setText( itm.label == null ? itm.procInfo.processName
							: itm.label );

					txt_name.setTypeface( Typeface.DEFAULT, Typeface.NORMAL );

					switch ( itm.procInfo.importance )
					{
						case RunningAppProcessInfo.IMPORTANCE_FOREGROUND :
							txt_name.setTextColor( Color.CYAN );
							break;
						case RunningAppProcessInfo.IMPORTANCE_VISIBLE :
							txt_name.setTextColor( Color.GREEN );
							break;
						case RunningAppProcessInfo.IMPORTANCE_SERVICE :
							txt_name.setTextColor( Color.GRAY );
							break;
						case RunningAppProcessInfo.IMPORTANCE_BACKGROUND :
							txt_name.setTextColor( Color.YELLOW );
							break;
						case RunningAppProcessInfo.IMPORTANCE_EMPTY :
						default :
							txt_name.setTextColor( Color.WHITE );
							break;
					}

					img_type.setImageDrawable( itm.icon );

					txt_mem.setText( Formatter.formatFileSize( ProcessManager.this,
							itm.rss * 4 * 1024 ) );

					long delta = itm.lastcputime == 0 ? 0
							: ( itm.cputime - itm.lastcputime );

					long cu = totalDelta == 0 ? 0 : ( delta * 100 / totalDelta );

					if ( cu < 0 )
					{
						cu = 0;
					}
					if ( cu > 100 )
					{
						cu = 100;
					}

					txt_cpu.setText( "" + cu + ' ' ); //$NON-NLS-1$
				}

				return view;
			}
		};

		getListView( ).setAdapter( adapter );
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		handler.post( task );
	}

	@Override
	protected void onPause( )
	{
		handler.removeCallbacks( task );
		handler.removeMessages( MSG_UPDATE );

		procCache.resCache.clear( );
		procCache.procList.clear( );

		super.onPause( );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 1 )
		{
			int t = data.getIntExtra( PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW );
			if ( t != getRefreshInterval( ) )
			{
				setRefreshInterval( t );
			}

			t = data.getIntExtra( PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME );
			if ( t != getSortOrderType( ) )
			{
				setSortOrderType( t );
			}

			t = data.getIntExtra( PREF_KEY_SORT_DIRECTION, ORDER_ASC );
			if ( t != getSortDirection( ) )
			{
				setSortDirection( t );
			}

			t = data.getIntExtra( PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN );
			if ( t != getIgnoreAction( ) )
			{
				setIgnoreAction( t );
			}

			ArrayList<String> list = data.getStringArrayListExtra( PREF_KEY_IGNORE_LIST );

			setIgnoreList( list );

			ignoreList.clear( );

			if ( list != null )
			{
				ignoreList.addAll( list );
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuItem mi = menu.add( Menu.NONE,
				R.id.mi_preference,
				Menu.NONE,
				R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_preference )
		{
			Intent intent = new Intent( Intent.ACTION_VIEW );

			intent.setClass( this, ProcessSettings.class );

			intent.putExtra( PREF_KEY_REFRESH_INTERVAL, getRefreshInterval( ) );
			intent.putExtra( PREF_KEY_SORT_ORDER_TYPE, getSortOrderType( ) );
			intent.putExtra( PREF_KEY_SORT_DIRECTION, getSortDirection( ) );
			intent.putExtra( PREF_KEY_IGNORE_ACTION, getIgnoreAction( ) );
			intent.putStringArrayListExtra( PREF_KEY_IGNORE_LIST,
					getIgnoreList( ) );

			startActivityForResult( intent, 1 );

			return true;
		}

		return false;
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		super.onCreateContextMenu( menu, v, menuInfo );

		int pos = ( (AdapterContextMenuInfo) menuInfo ).position;
		ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

		if ( rap != dummyInfo )
		{
			menu.setHeaderTitle( R.string.actions );
			menu.add( Menu.NONE, MI_DISPLAY, MI_DISPLAY, R.string.switch_to );

			if ( ignoreList.contains( rap.procInfo.processName ) )
			{
				menu.add( Menu.NONE, MI_ENDTASK, MI_ENDTASK, R.string.end_task )
						.setEnabled( false );
				menu.add( Menu.NONE, MI_IGNORE, MI_IGNORE, R.string.ignore )
						.setEnabled( false );
			}
			else
			{
				menu.add( Menu.NONE, MI_ENDTASK, MI_ENDTASK, R.string.end_task );
				menu.add( Menu.NONE, MI_IGNORE, MI_IGNORE, R.string.ignore );
			}

			menu.add( Menu.NONE, MI_DETAIL, MI_DETAIL, R.string.details );
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_DISPLAY )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

			if ( pos < getListView( ).getCount( ) )
			{
				ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

				Intent it = new Intent( "android.intent.action.MAIN" ); //$NON-NLS-1$
				it.addCategory( Intent.CATEGORY_LAUNCHER );

				List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
						0 );

				if ( acts != null )
				{
					String pkgName = rap.procInfo.processName;
					String self = this.getPackageName( );

					boolean started = false;

					for ( ResolveInfo ri : acts )
					{
						if ( pkgName.equals( ri.activityInfo.packageName ) )
						{
							if ( !pkgName.equals( self ) )
							{
								it.setClassName( ri.activityInfo.packageName,
										ri.activityInfo.name );

								it.addFlags( Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP );

								startActivity( it );
							}

							started = true;
							break;
						}
					}

					if ( !started )
					{
						Toast.makeText( this,
								R.string.error_switch_task,
								Toast.LENGTH_SHORT ).show( );
					}
				}
			}

			return true;
		}
		else if ( item.getItemId( ) == MI_ENDTASK )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

			if ( pos < getListView( ).getCount( ) )
			{
				ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

				ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

				String self = getPackageName( );

				if ( self.equals( rap.procInfo.processName ) )
				{
					am.restartPackage( self );
				}
				else
				{
					endProcess( am, rap.procInfo.pkgList );

					handler.removeCallbacks( task );
					handler.post( task );
				}
			}

			return true;
		}
		else if ( item.getItemId( ) == MI_IGNORE )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

			if ( pos < getListView( ).getCount( ) )
			{
				ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

				ignoreList.add( rap.procInfo.processName );

				setIgnoreList( ignoreList );

				if ( IGNORE_ACTION_HIDDEN == getIgnoreAction( ) )
				{
					handler.removeCallbacks( task );
					handler.post( task );
				}
			}

			return true;
		}
		else if ( item.getItemId( ) == MI_DETAIL )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

			if ( pos < getListView( ).getCount( ) )
			{
				ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

				StringBuffer sb = new StringBuffer( ).append( "<small>" ) //$NON-NLS-1$
						.append( getString( R.string.pid ) )
						.append( ": " ) //$NON-NLS-1$
						.append( rap.procInfo.pid )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.importance ) )
						.append( ": " ) //$NON-NLS-1$
						.append( rap.procInfo.importance )
						.append( "<br>LRU: " ) //$NON-NLS-1$
						.append( rap.procInfo.lru )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.pkg_name ) )
						.append( ":" ); //$NON-NLS-1$

				if ( rap.procInfo.pkgList != null )
				{
					for ( String pkg : rap.procInfo.pkgList )
					{
						if ( pkg != null )
						{
							sb.append( "<br>&nbsp;&nbsp;" + pkg ); //$NON-NLS-1$
						}
					}
				}

				sb.append( "</small>" ); //$NON-NLS-1$

				new AlertDialog.Builder( this ).setTitle( rap.label == null ? rap.procInfo.processName
						: rap.label )
						.setNeutralButton( R.string.close, null )
						.setMessage( Html.fromHtml( sb.toString( ) ) )
						.create( )
						.show( );

			}

			return true;
		}

		return super.onContextItemSelected( item );
	}

	private int getIgnoreAction( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_IGNORE_ACTION, IGNORE_ACTION_HIDDEN );
	}

	private void setIgnoreAction( int action )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_IGNORE_ACTION, action );
		et.commit( );
	}

	private ArrayList<String> getIgnoreList( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		String listVal = sp.getString( PREF_KEY_IGNORE_LIST, null );

		if ( listVal == null || listVal.length( ) == 0 )
		{
			return null;
		}

		StringTokenizer tokenizer = new StringTokenizer( listVal );
		ArrayList<String> list = new ArrayList<String>( );

		while ( tokenizer.hasMoreTokens( ) )
		{
			list.add( tokenizer.nextToken( ) );
		}

		return list.size( ) == 0 ? null : list;
	}

	private void setIgnoreList( Collection<String> list )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		if ( list == null || list.isEmpty( ) )
		{
			et.remove( PREF_KEY_IGNORE_LIST );
		}
		else
		{
			StringBuffer sb = new StringBuffer( );

			int i = 0;
			for ( String s : list )
			{
				if ( i > 0 )
				{
					sb.append( ' ' );
				}
				sb.append( s );

				i++;
			}

			et.putString( PREF_KEY_IGNORE_LIST, sb.toString( ) );
		}
		et.commit( );
	}

	private int getSortOrderType( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_SORT_ORDER_TYPE, ORDER_TYPE_NAME );
	}

	private void setSortOrderType( int type )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_SORT_ORDER_TYPE, type );
		et.commit( );
	}

	private int getSortDirection( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_SORT_DIRECTION, ORDER_ASC );
	}

	private void setSortDirection( int type )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_SORT_DIRECTION, type );
		et.commit( );
	}

	private int getRefreshInterval( )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		return sp.getInt( PREF_KEY_REFRESH_INTERVAL, REFRESH_LOW );
	}

	private void setRefreshInterval( int interval )
	{
		SharedPreferences sp = getPreferences( Context.MODE_PRIVATE );

		Editor et = sp.edit( );
		et.putInt( PREF_KEY_REFRESH_INTERVAL, interval );
		et.commit( );
	}

	private void endProcess( ActivityManager am, String[] pkgs )
	{
		if ( pkgs != null )
		{
			for ( String pkg : pkgs )
			{
				if ( pkg != null )
				{
					am.restartPackage( pkg );
				}
			}
		}
	}

	private void endAll( )
	{
		ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

		String self = this.getPackageName( );

		ListView lstProcs = getListView( );

		// skip the dummy info
		for ( int i = 1; i < lstProcs.getCount( ); i++ )
		{
			ProcessItem rap = (ProcessItem) lstProcs.getItemAtPosition( i );

			if ( !ignoreList.contains( rap.procInfo.processName )
					&& !self.equals( rap.procInfo.processName ) )
			{
				endProcess( am, rap.procInfo.pkgList );
			}
		}

		if ( !ignoreList.contains( self ) )
		{
			am.restartPackage( self );
		}
		else
		{
			handler.removeCallbacks( task );
			handler.post( task );
		}
	}

	private void updateProcess( List<RunningAppProcessInfo> list )
	{
		long newload = readCpuLoad( );

		if ( totalLoad != 0 )
		{
			totalDelta = newload - totalLoad;
		}

		totalLoad = newload;

		procCache.procList.clear( );

		if ( list != null )
		{
			PackageManager pm = getPackageManager( );

			int ignoreAction = getIgnoreAction( );

			String name;
			for ( RunningAppProcessInfo rap : list )
			{
				name = rap.processName;

				if ( name.startsWith( "com.google.process" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.phone" ) //$NON-NLS-1$
						|| name.startsWith( "android.process" ) //$NON-NLS-1$
						|| name.startsWith( "system" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.inputmethod" ) //$NON-NLS-1$
						|| name.startsWith( "com.android.alarmclock" ) ) //$NON-NLS-1$
				{
					continue;
				}

				if ( ignoreAction == IGNORE_ACTION_HIDDEN
						&& ignoreList.contains( name ) )
				{
					continue;
				}

				ProcessItem pi = procCache.resCache.get( name );

				if ( pi == null )
				{
					pi = new ProcessItem( );
					pi.procInfo = rap;

					readProcessInfo( pi, pm, true );

					procCache.resCache.put( name, pi );
				}
				else
				{
					pi.procInfo = rap;
					pi.lastcputime = pi.cputime;

					readProcessInfo( pi, pm, false );
				}

				procCache.procList.add( pi );
			}

			procCache.reOrder( getSortOrderType( ), getSortDirection( ) );
		}
	}

	private static long readCpuLoad( )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( "/proc/stat" ) ), //$NON-NLS-1$
					1024 );

			String line = reader.readLine( );

			if ( line != null && line.startsWith( "cpu " ) ) //$NON-NLS-1$
			{
				line = line.substring( 3 ).trim( );

				StringTokenizer tokens = new StringTokenizer( line );

				long totaltime = 0;
				int i = 0;
				String tk;

				while ( tokens.hasMoreTokens( ) && i < 7 )
				{
					tk = tokens.nextToken( );

					totaltime += Long.parseLong( tk );
					i++;
				}

				return totaltime;
			}
		}
		catch ( Exception e )
		{
			Log.e( ProcessManager.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( ProcessManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}

		return 0;
	}

	private static void readProcessStat( int pid, ProcessItem pi )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( "/proc/" //$NON-NLS-1$
					+ pid
					+ "/stat" ) ), //$NON-NLS-1$
					1024 );

			String line = reader.readLine( );

			if ( line != null )
			{
				line = line.trim( );

				int idx = line.lastIndexOf( ')' );

				if ( idx != -1 )
				{
					line = line.substring( idx + 1 ).trim( );

					StringTokenizer tokens = new StringTokenizer( line );

					String rss = null;
					String utime = null;
					String stime = null;

					int i = 0;
					String tk;

					// [11,12,21] for [utime,stime,rss]
					while ( tokens.hasMoreTokens( ) )
					{
						tk = tokens.nextToken( );

						if ( i == 11 )
						{
							utime = tk;
						}
						else if ( i == 12 )
						{
							stime = tk;
						}
						else if ( i == 21 )
						{
							rss = tk;
						}

						if ( rss != null )
						{
							break;
						}

						i++;
					}

					if ( utime != null )
					{
						pi.cputime = Long.parseLong( utime );
					}

					if ( stime != null )
					{
						pi.cputime += Long.parseLong( stime );
					}

					if ( rss != null )
					{
						pi.rss = Long.parseLong( rss );
					}
				}
			}
		}
		catch ( Exception e )
		{
			Log.e( ProcessManager.class.getName( ), e.getLocalizedMessage( ), e );
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
					Log.e( ProcessManager.class.getName( ),
							e.getLocalizedMessage( ),
							e );
				}
			}
		}
	}

	private void readProcessInfo( ProcessItem proc, PackageManager pm,
			boolean isNew )
	{
		if ( isNew && pm != null )
		{
			try
			{
				ApplicationInfo ai = pm.getApplicationInfo( proc.procInfo.processName,
						0 );

				if ( ai != null )
				{
					CharSequence label = pm.getApplicationLabel( ai );

					if ( label != null )
					{
						proc.label = label.toString( );
					}

					Drawable icon = pm.getApplicationIcon( ai );

					if ( icon == null )
					{
						icon = pm.getDefaultActivityIcon( );
					}

					proc.icon = icon;
				}
			}
			catch ( NameNotFoundException e )
			{
				int idx = proc.procInfo.processName.indexOf( ':' );

				if ( idx != -1 )
				{
					String name = proc.procInfo.processName.substring( 0, idx );

					try
					{
						ApplicationInfo ai = pm.getApplicationInfo( name, 0 );

						if ( ai != null )
						{
							CharSequence label = pm.getApplicationLabel( ai );

							if ( label != null )
							{
								proc.label = label.toString( )
										+ proc.procInfo.processName.substring( idx );
							}
						}
					}
					catch ( NameNotFoundException e1 )
					{
						// ignore this exception
					}
				}
			}
		}

		if ( proc.procInfo.pid != 0 )
		{
			readProcessStat( proc.procInfo.pid, proc );
		}
	}

	/**
	 * ProcessSettings
	 */
	public static final class ProcessSettings extends PreferenceActivity
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

			Preference perfInterval = new Preference( this );
			perfInterval.setKey( PREF_KEY_REFRESH_INTERVAL );
			perfInterval.setTitle( R.string.update_speed );
			pc.addPreference( perfInterval );

			pc = new PreferenceCategory( this );
			pc.setTitle( R.string.sort );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfSortType = new Preference( this );
			perfSortType.setKey( PREF_KEY_SORT_ORDER_TYPE );
			perfSortType.setTitle( R.string.sort_type );
			pc.addPreference( perfSortType );

			Preference perfSortDirection = new Preference( this );
			perfSortDirection.setKey( PREF_KEY_SORT_DIRECTION );
			perfSortDirection.setTitle( R.string.sort_direction );
			pc.addPreference( perfSortDirection );

			pc = new PreferenceCategory( this );
			pc.setTitle( R.string.ignore );
			getPreferenceScreen( ).addPreference( pc );

			Preference perfIgnoreAction = new Preference( this );
			perfIgnoreAction.setKey( PREF_KEY_IGNORE_ACTION );
			perfIgnoreAction.setTitle( R.string.ignored_as );
			pc.addPreference( perfIgnoreAction );

			Preference perfIgnoreList = new Preference( this );
			perfIgnoreList.setKey( PREF_KEY_IGNORE_LIST );
			perfIgnoreList.setTitle( R.string.ignored_list );
			pc.addPreference( perfIgnoreList );

			refreshInterval( );
			refreshSortType( );
			refreshSortDirection( );
			refreshIgnoreAction( );
			refreshIgnoreList( );

			setResult( RESULT_OK, getIntent( ) );
		}

		private void refreshInterval( )
		{
			int interval = getIntent( ).getIntExtra( PREF_KEY_REFRESH_INTERVAL,
					REFRESH_NORMAL );

			CharSequence label = getString( R.string.normal );
			switch ( interval )
			{
				case REFRESH_HIGH :
					label = getString( R.string.high );
					break;
				case REFRESH_LOW :
					label = getString( R.string.low );
					break;
				case REFRESH_PAUSED :
					label = getString( R.string.paused );
					break;
			}

			findPreference( PREF_KEY_REFRESH_INTERVAL ).setSummary( label );
		}

		private void refreshSortType( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME );

			String label = null;
			switch ( type )
			{
				case ORDER_TYPE_NAME :
					label = getString( R.string.name );
					break;
				case ORDER_TYPE_IMPORTANCE :
					label = getString( R.string.importance );
					break;
				case ORDER_TYPE_MEM :
					label = getString( R.string.memory_usage );
					break;
				case ORDER_TYPE_CPU :
					label = getString( R.string.cpu_usage );
					break;
			}

			findPreference( PREF_KEY_SORT_ORDER_TYPE ).setSummary( label );
		}

		private void refreshSortDirection( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );

			String label = type == ORDER_ASC ? getString( R.string.ascending )
					: getString( R.string.descending );

			findPreference( PREF_KEY_SORT_DIRECTION ).setSummary( label );
		}

		private void refreshIgnoreAction( )
		{
			int action = getIntent( ).getIntExtra( PREF_KEY_IGNORE_ACTION,
					IGNORE_ACTION_HIDDEN );

			findPreference( PREF_KEY_IGNORE_ACTION ).setSummary( action == IGNORE_ACTION_HIDDEN ? R.string.hidden
					: R.string.protect );
		}

		private void refreshIgnoreList( )
		{
			ArrayList<String> list = getIntent( ).getStringArrayListExtra( PREF_KEY_IGNORE_LIST );

			Preference pref = findPreference( PREF_KEY_IGNORE_LIST );

			if ( list == null || list.size( ) == 0 )
			{
				pref.setSummary( getString( R.string.single_ignored, 0 ) );
				pref.setEnabled( false );
			}
			else
			{
				if ( list.size( ) == 1 )
				{
					pref.setSummary( getString( R.string.single_ignored, 1 ) );
				}
				else
				{
					pref.setSummary( getString( R.string.multi_ignored,
							list.size( ) ) );
				}

				pref.setEnabled( true );
			}
		}

		private String getProcessLabel( String name, PackageManager pm )
		{
			if ( pm != null )
			{
				try
				{
					ApplicationInfo ai = pm.getApplicationInfo( name, 0 );

					if ( ai != null )
					{
						CharSequence label = pm.getApplicationLabel( ai );

						if ( label != null )
						{
							name = label.toString( );
						}
					}
				}
				catch ( NameNotFoundException e )
				{
					int idx = name.indexOf( ':' );

					if ( idx != -1 )
					{
						String prefix = name.substring( 0, idx );

						try
						{
							ApplicationInfo ai = pm.getApplicationInfo( prefix,
									0 );

							if ( ai != null )
							{
								CharSequence label = pm.getApplicationLabel( ai );

								if ( label != null )
								{
									name = label.toString( )
											+ name.substring( idx );
								}
							}
						}
						catch ( NameNotFoundException e1 )
						{
							// ignore this exception
						}
					}
				}
			}

			return name;
		}

		@Override
		public boolean onPreferenceTreeClick(
				PreferenceScreen preferenceScreen, Preference preference )
		{
			final Intent it = getIntent( );

			if ( PREF_KEY_REFRESH_INTERVAL.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_REFRESH_INTERVAL, which );

						dialog.dismiss( );

						refreshInterval( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.update_speed )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new CharSequence[]{
								getString( R.string.high ),
								getString( R.string.normal ),
								getString( R.string.low ),
								getString( R.string.paused ),
						},
								it.getIntExtra( PREF_KEY_REFRESH_INTERVAL,
										REFRESH_NORMAL ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SORT_ORDER_TYPE.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_SORT_ORDER_TYPE, which );

						dialog.dismiss( );

						refreshSortType( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.sort_type )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.name ),
								getString( R.string.importance ),
								getString( R.string.memory_usage ),
								getString( R.string.cpu_usage ),
						},
								it.getIntExtra( PREF_KEY_SORT_ORDER_TYPE,
										ORDER_TYPE_NAME ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SORT_DIRECTION.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_SORT_DIRECTION,
								which == 0 ? ORDER_ASC : ORDER_DESC );

						dialog.dismiss( );

						refreshSortDirection( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.sort_direction )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.ascending ),
								getString( R.string.descending ),
						},
								it.getIntExtra( PREF_KEY_SORT_DIRECTION,
										ORDER_ASC ) == ORDER_ASC ? 0 : 1,
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_IGNORE_ACTION.equals( preference.getKey( ) ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_IGNORE_ACTION, which );

						dialog.dismiss( );

						refreshIgnoreAction( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.ignored_as )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.hidden ),
								getString( R.string.protect ),
						},
								it.getIntExtra( PREF_KEY_IGNORE_ACTION,
										IGNORE_ACTION_HIDDEN ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_IGNORE_LIST.equals( preference.getKey( ) ) )
			{
				final ArrayList<String> list = it.getStringArrayListExtra( PREF_KEY_IGNORE_LIST );
				final boolean[] state = new boolean[list.size( )];

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						ArrayList<String> nlist = new ArrayList<String>( );

						for ( int i = 0; i < list.size( ); i++ )
						{
							if ( !state[i] )
							{
								nlist.add( list.get( i ) );
							}
						}

						if ( list.size( ) == nlist.size( ) )
						{
							Toast.makeText( ProcessSettings.this,
									R.string.no_item_remove,
									Toast.LENGTH_SHORT ).show( );
						}
						else
						{
							if ( nlist.size( ) == 0 )
							{
								it.removeExtra( PREF_KEY_IGNORE_LIST );
							}
							else
							{
								it.putStringArrayListExtra( PREF_KEY_IGNORE_LIST,
										nlist );
							}

							dialog.dismiss( );

							refreshIgnoreList( );
						}
					}
				};

				OnMultiChoiceClickListener multiListener = new OnMultiChoiceClickListener( ) {

					public void onClick( DialogInterface dialog, int which,
							boolean isChecked )
					{
						state[which] = isChecked;
					}
				};

				final PackageManager pm = getPackageManager( );
				final String[] labels = new String[list.size( )];

				for ( int i = 0; i < list.size( ); i++ )
				{
					labels[i] = getProcessLabel( list.get( i ), pm );
				}

				new AlertDialog.Builder( this ).setTitle( R.string.ignored_list )
						.setPositiveButton( R.string.remove, listener )
						.setNegativeButton( R.string.close, null )
						.setMultiChoiceItems( labels, state, multiListener )
						.create( )
						.show( );

				return true;
			}

			return false;
		}
	}

	/**
	 * ProcessItem
	 */
	private static final class ProcessItem
	{

		RunningAppProcessInfo procInfo;

		String label;

		Drawable icon;

		long rss;

		long cputime;

		long lastcputime;

		@Override
		public boolean equals( Object o )
		{
			if ( !( o instanceof ProcessItem ) )
			{
				return false;
			}

			return this.procInfo.pid == ( (ProcessItem) o ).procInfo.pid;
		}
	}

	/**
	 * ProcessCache
	 */
	private static final class ProcessCache
	{

		HashMap<String, ProcessItem> resCache = new HashMap<String, ProcessItem>( );

		ArrayList<ProcessItem> procList = new ArrayList<ProcessItem>( );

		synchronized void reOrder( int type, final int direction )
		{
			switch ( type )
			{
				case ORDER_TYPE_NAME :
					Collections.sort( procList, new Comparator<ProcessItem>( ) {

						Collator clt = Collator.getInstance( );

						public int compare( ProcessItem obj1, ProcessItem obj2 )
						{
							String lb1 = obj1.label == null ? obj1.procInfo.processName
									: obj1.label;
							String lb2 = obj2.label == null ? obj2.procInfo.processName
									: obj2.label;

							return clt.compare( lb1, lb2 ) * direction;
						}
					} );
					break;
				case ORDER_TYPE_IMPORTANCE :
					Collections.sort( procList, new Comparator<ProcessItem>( ) {

						public int compare( ProcessItem obj1, ProcessItem obj2 )
						{
							// result should be reversed
							return ( obj2.procInfo.importance - obj1.procInfo.importance )
									* direction;

						}
					} );
					break;
				case ORDER_TYPE_MEM :
					Collections.sort( procList, new Comparator<ProcessItem>( ) {

						public int compare( ProcessItem obj1, ProcessItem obj2 )
						{
							return (int) ( obj1.rss - obj2.rss ) * direction;

						}
					} );
					break;
				case ORDER_TYPE_CPU :
					Collections.sort( procList, new Comparator<ProcessItem>( ) {

						public int compare( ProcessItem obj1, ProcessItem obj2 )
						{
							return (int) ( ( obj1.lastcputime == 0 ? 0
									: ( obj1.cputime - obj1.lastcputime ) ) - ( obj2.lastcputime == 0 ? 0
									: ( obj2.cputime - obj2.lastcputime ) ) )
									* direction;
						}
					} );
					break;
			}
		}
	}
}
