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
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * ProcessManager
 */
public final class ProcessManager extends ListActivity implements Constants
{

	private static final int MSG_REFRESH_PKG_LABEL = MSG_PRIVATE + 1;
	private static final int MSG_REFRESH_PKG_ICON = MSG_PRIVATE + 2;

	private static final String PREF_KEY_IGNORE_ACTION = "ignore_action"; //$NON-NLS-1$
	private static final String PREF_KEY_IGNORE_LIST = "ignore_list"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_MEM = "show_mem"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_CPU = "show_cpu"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_SYS_PROC = "show_sys_proc"; //$NON-NLS-1$
	private static final String PREF_KEY_SHOW_KILL_WARN = "show_kill_warn"; //$NON-NLS-1$

	private static final int ORDER_TYPE_NAME = 0;
	private static final int ORDER_TYPE_IMPORTANCE = 1;
	private static final int ORDER_TYPE_MEM = 2;
	private static final int ORDER_TYPE_CPU = 3;

	private static final int ACTION_MENU = 0;
	private static final int ACTION_SWITCH = 1;
	private static final int ACTION_END = 2;
	private static final int ACTION_END_OTHERS = 3;
	private static final int ACTION_IGNORE = 4;
	private static final int ACTION_DETAILS = 5;

	private static final int IGNORE_ACTION_HIDDEN = 0;
	private static final int IGNORE_ACTION_PROTECTED = 1;

	ProcessCache procCache;

	long totalLoad, totalDelta, totalWork, workDelta;

	LinkedHashSet<String> ignoreList;

	ResourceUpdaterThread resUpdater;

	private byte[] buf = new byte[512];

	Handler handler = new Handler( ) {

		public void handleMessage( android.os.Message msg )
		{
			switch ( msg.what )
			{
				case MSG_INIT_OK :

					if ( resUpdater != null )
					{
						resUpdater.aborted = true;
					}

					( resUpdater = new ResourceUpdaterThread( ProcessManager.this,
							procCache,
							handler ) ).start( );

					ArrayAdapter<ProcessItem> adapter = (ArrayAdapter<ProcessItem>) getListView( ).getAdapter( );

					adapter.setNotifyOnChange( false );

					synchronized ( procCache )
					{
						adapter.clear( );

						ArrayList<ProcessItem> localList = procCache.procList;

						for ( int i = 0, size = localList.size( ); i < size; i++ )
						{
							adapter.add( localList.get( i ) );
						}
					}

					adapter.notifyDataSetChanged( );

					refreshHeader( );

					int interval = Util.getIntOption( ProcessManager.this,
							PREF_KEY_REFRESH_INTERVAL,
							REFRESH_LOW );

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
					break;
				case MSG_REFRESH_PKG_LABEL :

					adapter = (ArrayAdapter<ProcessItem>) (ArrayAdapter<ProcessItem>) getListView( ).getAdapter( );

					if ( msg.arg1 == 1 )
					{
						adapter.setNotifyOnChange( false );

						synchronized ( procCache )
						{
							adapter.clear( );

							ArrayList<ProcessItem> localList = procCache.procList;

							for ( int i = 0, size = localList.size( ); i < size; i++ )
							{
								adapter.add( localList.get( i ) );
							}
						}
					}

					adapter.notifyDataSetChanged( );
					break;
				case MSG_REFRESH_PKG_ICON :

					( (ArrayAdapter<ProcessItem>) getListView( ).getAdapter( ) ).notifyDataSetChanged( );
					break;
			}
		};
	};

	Runnable task = new Runnable( ) {

		public void run( )
		{
			ActivityManager am = (ActivityManager) getSystemService( ACTIVITY_SERVICE );

			List<RunningAppProcessInfo> raps = am.getRunningAppProcesses( );

			updateProcess( raps );

			handler.sendEmptyMessage( MSG_INIT_OK );
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		setContentView( R.layout.proc_lst_view );

		registerForContextMenu( getListView( ) );

		procCache = new ProcessCache( );

		ignoreList = new LinkedHashSet<String>( );

		ArrayList<String> list = getIgnoreList( getPreferences( Context.MODE_PRIVATE ) );

		if ( list != null )
		{
			ignoreList.addAll( list );
		}

		View listHeader = findViewById( R.id.list_head );
		listHeader.setOnClickListener( new View.OnClickListener( ) {

			public void onClick( View v )
			{
				boolean showWarning = Util.getBooleanOption( ProcessManager.this,
						PREF_KEY_SHOW_KILL_WARN );

				if ( showWarning )
				{
					OnClickListener listener = new OnClickListener( ) {

						public void onClick( DialogInterface dialog, int which )
						{
							endAllExcept( null );
						}
					};

					new AlertDialog.Builder( ProcessManager.this ).setTitle( R.string.warning )
							.setMessage( R.string.end_all_prompt )
							.setPositiveButton( android.R.string.ok, listener )
							.setNegativeButton( android.R.string.cancel, null )
							.create( )
							.show( );
				}
				else
				{
					endAllExcept( null );
				}

			}
		} );

		getListView( ).setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				final ProcessItem rap = (ProcessItem) parent.getItemAtPosition( position );

				boolean showWarning = Util.getBooleanOption( ProcessManager.this,
						PREF_KEY_SHOW_KILL_WARN );

				final int action = Util.getIntOption( ProcessManager.this,
						PREF_KEY_DEFAULT_TAP_ACTION,
						ACTION_MENU );

				if ( action == ACTION_END || action == ACTION_IGNORE )
				{
					boolean protect = ignoreList.contains( rap.procInfo.processName )
							|| rap.sys;

					if ( protect )
					{
						return;
					}
				}

				if ( showWarning
						&& ( action == ACTION_END || action == ACTION_END_OTHERS ) )
				{

					OnClickListener listener = new OnClickListener( ) {

						public void onClick( DialogInterface dialog, int which )
						{
							handleAction( rap, action );
						}
					};

					new AlertDialog.Builder( ProcessManager.this ).setTitle( R.string.warning )
							.setMessage( action == ACTION_END ? R.string.end_prompt
									: R.string.end_other_prompt )
							.setPositiveButton( android.R.string.ok, listener )
							.setNegativeButton( android.R.string.cancel, null )
							.create( )
							.show( );
				}
				else
				{
					handleAction( rap, action );
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

				if ( position >= getCount( ) )
				{
					return view;
				}

				ProcessItem itm = getItem( position );

				img_type = (ImageView) view.findViewById( R.id.img_proc_icon );
				txt_name = (TextView) view.findViewById( R.id.txt_proc_name );
				txt_mem = (TextView) view.findViewById( R.id.txt_mem );
				txt_cpu = (TextView) view.findViewById( R.id.txt_cpu );

				boolean showMem = Util.getBooleanOption( ProcessManager.this,
						PREF_KEY_SHOW_MEM );
				boolean showCpu = Util.getBooleanOption( ProcessManager.this,
						PREF_KEY_SHOW_CPU );

				String lb = itm.label == null ? itm.procInfo.processName
						: itm.label;
				if ( itm.sys )
				{
					lb += " *"; //$NON-NLS-1$
				}
				else if ( ignoreList.contains( itm.procInfo.processName ) )
				{
					lb += " ~"; //$NON-NLS-1$
				}
				txt_name.setText( lb );

				switch ( itm.procInfo.importance )
				{
					case RunningAppProcessInfo.IMPORTANCE_FOREGROUND :
						txt_name.setTextColor( Color.CYAN );
						break;
					case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE :
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

				if ( showMem )
				{
					txt_mem.setVisibility( View.VISIBLE );
					txt_mem.setText( itm.mem );
				}
				else
				{
					txt_mem.setVisibility( View.GONE );
				}

				if ( showCpu )
				{
					txt_cpu.setVisibility( View.VISIBLE );

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

					txt_cpu.setText( String.valueOf( cu ) );
				}
				else
				{
					txt_cpu.setVisibility( View.GONE );
				}

				return view;
			}
		};

		getListView( ).setAdapter( adapter );
	}

	@Override
	protected void onDestroy( )
	{
		( (ArrayAdapter<ProcessItem>) getListView( ).getAdapter( ) ).clear( );

		procCache.clear( );

		super.onDestroy( );
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
		handler.removeMessages( MSG_INIT_OK );

		if ( resUpdater != null )
		{
			resUpdater.aborted = true;
			resUpdater = null;
		}

		super.onPause( );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode,
			Intent data )
	{
		if ( requestCode == 1 && data != null )
		{
			Util.updateIntOption( data,
					this,
					PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME );
			Util.updateIntOption( data,
					this,
					PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );
			Util.updateIntOption( data,
					this,
					PREF_KEY_IGNORE_ACTION,
					IGNORE_ACTION_HIDDEN );
			Util.updateIntOption( data,
					this,
					PREF_KEY_DEFAULT_TAP_ACTION,
					ACTION_MENU );

			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_MEM );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_CPU );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_SYS_PROC );
			Util.updateBooleanOption( data, this, PREF_KEY_SHOW_KILL_WARN );

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
				MI_PREFERENCE,
				Menu.NONE,
				R.string.preference );
		mi.setIcon( android.R.drawable.ic_menu_preferences );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_PREFERENCE )
		{
			Intent it = new Intent( this, ProcessSettings.class );

			it.putExtra( PREF_KEY_REFRESH_INTERVAL, Util.getIntOption( this,
					PREF_KEY_REFRESH_INTERVAL,
					REFRESH_LOW ) );
			it.putExtra( PREF_KEY_SORT_ORDER_TYPE, Util.getIntOption( this,
					PREF_KEY_SORT_ORDER_TYPE,
					ORDER_TYPE_NAME ) );
			it.putExtra( PREF_KEY_SORT_DIRECTION,
					Util.getIntOption( this, PREF_KEY_SORT_DIRECTION, ORDER_ASC ) );
			it.putExtra( PREF_KEY_IGNORE_ACTION, Util.getIntOption( this,
					PREF_KEY_IGNORE_ACTION,
					IGNORE_ACTION_HIDDEN ) );
			it.putExtra( PREF_KEY_DEFAULT_TAP_ACTION, Util.getIntOption( this,
					PREF_KEY_DEFAULT_TAP_ACTION,
					ACTION_MENU ) );
			it.putStringArrayListExtra( PREF_KEY_IGNORE_LIST,
					getIgnoreList( getPreferences( Context.MODE_PRIVATE ) ) );
			it.putExtra( PREF_KEY_SHOW_MEM,
					Util.getBooleanOption( this, PREF_KEY_SHOW_MEM ) );
			it.putExtra( PREF_KEY_SHOW_CPU,
					Util.getBooleanOption( this, PREF_KEY_SHOW_CPU ) );
			it.putExtra( PREF_KEY_SHOW_SYS_PROC,
					Util.getBooleanOption( this, PREF_KEY_SHOW_SYS_PROC ) );
			it.putExtra( PREF_KEY_SHOW_KILL_WARN,
					Util.getBooleanOption( this, PREF_KEY_SHOW_KILL_WARN ) );

			startActivityForResult( it, 1 );

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

		menu.setHeaderTitle( R.string.actions );
		menu.add( Menu.NONE, MI_DISPLAY, Menu.NONE, R.string.switch_to );

		boolean protect = ignoreList.contains( rap.procInfo.processName )
				|| rap.sys;

		if ( protect )
		{
			menu.add( Menu.NONE, MI_ENDTASK, Menu.NONE, R.string.end_task )
					.setEnabled( false );
		}
		else
		{
			menu.add( Menu.NONE, MI_ENDTASK, Menu.NONE, R.string.end_task );
		}

		menu.add( Menu.NONE, MI_END_OTHERS, Menu.NONE, R.string.end_others );

		if ( protect )
		{
			menu.add( Menu.NONE, MI_IGNORE, Menu.NONE, R.string.ignore )
					.setEnabled( false );
		}
		else
		{
			menu.add( Menu.NONE, MI_IGNORE, Menu.NONE, R.string.ignore );
		}

		menu.add( Menu.NONE, MI_DETAILS, Menu.NONE, R.string.details );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;

		if ( pos < getListView( ).getCount( ) )
		{
			ProcessItem rap = (ProcessItem) getListView( ).getItemAtPosition( pos );

			if ( item.getItemId( ) == MI_DISPLAY )
			{
				handleAction( rap, ACTION_SWITCH );

				return true;
			}
			else if ( item.getItemId( ) == MI_ENDTASK )
			{
				handleAction( rap, ACTION_END );

				return true;
			}
			else if ( item.getItemId( ) == MI_END_OTHERS )
			{
				handleAction( rap, ACTION_END_OTHERS );

				return true;
			}
			else if ( item.getItemId( ) == MI_IGNORE )
			{
				handleAction( rap, ACTION_IGNORE );

				return true;
			}
			else if ( item.getItemId( ) == MI_DETAILS )
			{
				handleAction( rap, ACTION_DETAILS );

				return true;
			}
		}

		return super.onContextItemSelected( item );
	}

	static ArrayList<String> getIgnoreList( SharedPreferences sp )
	{
		if ( sp == null )
		{
			return null;
		}

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

	void handleAction( final ProcessItem rap, int action )
	{
		switch ( action )
		{
			case ACTION_END :

				if ( !ignoreList.contains( rap.procInfo.processName )
						&& !rap.sys )
				{
					ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

					String self = getPackageName( );

					if ( self.equals( rap.procInfo.processName ) )
					{
						Util.killSelf( handler, ProcessManager.this, am, self );
					}
					else
					{
						endProcess( am, rap.procInfo.pkgList, self );

						handler.removeCallbacks( task );
						handler.post( task );
					}
				}

				break;
			case ACTION_END_OTHERS :

				endAllExcept( rap.procInfo.processName );

				break;
			case ACTION_SWITCH :

				String pkgName = rap.procInfo.processName;

				if ( !pkgName.equals( this.getPackageName( ) ) )
				{
					Intent it = new Intent( "android.intent.action.MAIN" ); //$NON-NLS-1$
					it.addCategory( Intent.CATEGORY_LAUNCHER );

					List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
							0 );

					if ( acts != null )
					{
						boolean started = false;

						for ( int i = 0, size = acts.size( ); i < size; i++ )
						{
							ResolveInfo ri = acts.get( i );

							if ( pkgName.equals( ri.activityInfo.packageName ) )
							{
								it.setClassName( ri.activityInfo.packageName,
										ri.activityInfo.name );

								it.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK )
										.addFlags( Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY );

								try
								{
									startActivity( it );

									started = true;
								}
								catch ( Exception e )
								{
									Log.e( ProcessManager.class.getName( ),
											"Cannot start activity: " + pkgName, //$NON-NLS-1$
											e );
								}

								break;
							}
						}

						if ( !started )
						{
							Util.shortToast( this, R.string.error_switch_task );
						}
					}
				}

				break;
			case ACTION_IGNORE :

				if ( !ignoreList.contains( rap.procInfo.processName )
						&& !rap.sys )
				{
					ignoreList.add( rap.procInfo.processName );

					setIgnoreList( ignoreList );

					if ( IGNORE_ACTION_HIDDEN == Util.getIntOption( this,
							PREF_KEY_IGNORE_ACTION,
							IGNORE_ACTION_HIDDEN ) )
					{
						handler.removeCallbacks( task );
						handler.post( task );
					}
				}

				break;
			case ACTION_DETAILS :

				String[] status = readProcStatus( rap.procInfo.pid );

				StringBuffer sb = new StringBuffer( ).append( "<small>" ) //$NON-NLS-1$
						.append( getString( R.string.proc_name ) )
						.append( ": " ) //$NON-NLS-1$
						.append( rap.procInfo.processName )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.pid ) )
						.append( ": " ) //$NON-NLS-1$
						.append( rap.procInfo.pid )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.uid ) )
						.append( ": " ) //$NON-NLS-1$
						.append( status == null ? "" : status[1] ) //$NON-NLS-1$
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.gid ) )
						.append( ": " ) //$NON-NLS-1$
						.append( status == null ? "" : status[2] ) //$NON-NLS-1$
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.state ) )
						.append( ": " ) //$NON-NLS-1$
						.append( status == null ? "" : status[0] ) //$NON-NLS-1$
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.threads ) )
						.append( ": " ) //$NON-NLS-1$
						.append( status == null ? "" : status[3] ) //$NON-NLS-1$
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.importance ) )
						.append( ": " ) //$NON-NLS-1$
						.append( rap.procInfo.importance )
						.append( "<br>LRU: " ) //$NON-NLS-1$
						.append( rap.procInfo.lru )
						.append( "<br>" ) //$NON-NLS-1$
						.append( getString( R.string.pkg_name ) )
						.append( ": " ); //$NON-NLS-1$

				if ( rap.procInfo.pkgList != null )
				{
					int i = 0;

					for ( String pkg : rap.procInfo.pkgList )
					{
						if ( pkg != null )
						{
							if ( i > 0 )
							{
								sb.append( ", " ); //$NON-NLS-1$
							}
							sb.append( pkg );
							i++;
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

				break;
			case ACTION_MENU :

				final boolean protect = ignoreList.contains( rap.procInfo.processName )
						|| rap.sys;

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						dialog.dismiss( );

						// bypass the 'showMenu' action offset
						int action = which + 1;

						if ( !protect
								|| ( action != ACTION_END && action != ACTION_IGNORE ) )
						{
							handleAction( rap, action );
						}
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.actions )
						.setItems( new CharSequence[]{
						getString( R.string.switch_to ),
								protect ? Html.fromHtml( "<font color=\"#848484\">" //$NON-NLS-1$
										+ getString( R.string.end_task )
										+ "</font>" ) //$NON-NLS-1$
										: getString( R.string.end_task ),
								getString( R.string.end_others ),
								protect ? Html.fromHtml( "<font color=\"#848484\">" //$NON-NLS-1$
										+ getString( R.string.ignore )
										+ "</font>" ) //$NON-NLS-1$
										: getString( R.string.ignore ),
								getString( R.string.details )
						},
								listener )
						.create( )
						.show( );

				break;
		}
	}

	private void endProcess( ActivityManager am, String[] pkgs, String self )
	{
		if ( pkgs != null )
		{
			for ( String pkg : pkgs )
			{
				if ( pkg != null )
				{
					int subKillType = Util.killable( pkg, self, ignoreList );

					if ( subKillType == 0 )
					{
						am.restartPackage( pkg );
					}
				}
			}
		}
	}

	void endAllExcept( String exception )
	{
		ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

		String self = this.getPackageName( );

		ListView lstProcs = getListView( );

		// skip the dummy info
		for ( int i = 1, size = lstProcs.getCount( ); i < size; i++ )
		{
			ProcessItem rap = (ProcessItem) lstProcs.getItemAtPosition( i );

			String procName = rap.procInfo.processName;

			if ( !ignoreList.contains( procName )
					&& !self.equals( procName )
					&& !rap.sys
					&& !procName.equals( exception ) )
			{
				endProcess( am, rap.procInfo.pkgList, self );
			}
		}

		if ( !ignoreList.contains( self ) && !self.equals( exception ) )
		{
			Util.killSelf( handler, ProcessManager.this, am, self );
		}
		else
		{
			handler.removeCallbacks( task );
			handler.post( task );
		}
	}

	void refreshHeader( )
	{
		TextView txt_head_mem = (TextView) findViewById( R.id.txt_head_mem );
		TextView txt_head_cpu = (TextView) findViewById( R.id.txt_head_cpu );

		boolean showMem = Util.getBooleanOption( ProcessManager.this,
				PREF_KEY_SHOW_MEM );
		boolean showCpu = Util.getBooleanOption( ProcessManager.this,
				PREF_KEY_SHOW_CPU );

		StringBuilder totalString = null;

		if ( showMem )
		{
			txt_head_mem.setVisibility( View.VISIBLE );

			long[] mem = SysInfoManager.getMemState( ProcessManager.this );

			if ( mem != null )
			{
				totalString = new StringBuilder( );
				totalString.append( getString( R.string.free ) )
						.append( ": " ) //$NON-NLS-1$
						.append( Formatter.formatFileSize( ProcessManager.this,
								mem[2] ) );
			}
		}
		else
		{
			txt_head_mem.setVisibility( View.GONE );
		}

		if ( showCpu )
		{
			txt_head_cpu.setVisibility( View.VISIBLE );

			long cu = totalDelta == 0 ? 0 : ( workDelta * 100 / totalDelta );

			if ( cu < 0 )
			{
				cu = 0;
			}
			if ( cu > 100 )
			{
				cu = 100;
			}

			if ( totalString == null )
			{
				totalString = new StringBuilder( );
				totalString.append( getString( R.string.load ) ).append( ": " ) //$NON-NLS-1$
						.append( cu )
						.append( '%' );
			}
			else
			{
				totalString.append( "  " ) //$NON-NLS-1$
						.append( getString( R.string.load ) )
						.append( ": " ) //$NON-NLS-1$
						.append( cu )
						.append( '%' );
			}
		}
		else
		{
			txt_head_cpu.setVisibility( View.GONE );
		}

		View header = findViewById( R.id.list_head );

		if ( totalString == null )
		{
			header.setVisibility( View.GONE );
		}
		else
		{
			TextView txt_head_total = (TextView) findViewById( R.id.txt_head_total );
			txt_head_total.setText( totalString );

			header.setVisibility( View.VISIBLE );
		}
	}

	void updateProcess( List<RunningAppProcessInfo> list )
	{
		boolean showCpu = Util.getBooleanOption( this, PREF_KEY_SHOW_CPU );

		if ( showCpu )
		{
			long[] loads = readCpuLoad( );

			long newload = loads == null ? 0 : ( loads[0] + loads[1] );

			if ( totalLoad != 0 )
			{
				totalDelta = newload - totalLoad;
			}

			totalLoad = newload;

			long newWork = loads == null ? 0 : loads[0];

			if ( totalWork != 0 )
			{
				workDelta = newWork - totalWork;
			}

			totalWork = newWork;
		}

		synchronized ( procCache )
		{
			procCache.procList.clear( );

			if ( list != null )
			{
				int ignoreAction = Util.getIntOption( this,
						PREF_KEY_IGNORE_ACTION,
						IGNORE_ACTION_HIDDEN );
				boolean showMem = Util.getBooleanOption( this,
						PREF_KEY_SHOW_MEM );
				boolean showSys = Util.getBooleanOption( this,
						PREF_KEY_SHOW_SYS_PROC );

				String name;
				boolean isSys;

				for ( int i = 0, size = list.size( ); i < size; i++ )
				{
					RunningAppProcessInfo rap = list.get( i );

					name = rap.processName;

					isSys = Util.isSysProcess( name );

					if ( isSys && !showSys )
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
						pi.sys = isSys;
					}
					else
					{
						pi.procInfo = rap;
						pi.sys = isSys;
						pi.lastcputime = pi.cputime;
					}

					if ( rap.pid != 0 && ( showMem || showCpu ) )
					{
						readProcessStat( this, buf, pi, showMem, showCpu );
					}

					procCache.procList.add( pi );
				}

				procCache.reOrder( Util.getIntOption( this,
						PREF_KEY_SORT_ORDER_TYPE,
						ORDER_TYPE_NAME ), Util.getIntOption( this,
						PREF_KEY_SORT_DIRECTION,
						ORDER_ASC ) );
			}
		}
	}

	/**
	 * @return [worktime, idletime]
	 */
	static long[] readCpuLoad( )
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( "/proc/stat" ) ), //$NON-NLS-1$
					256 );

			String line = reader.readLine( );

			if ( line != null && line.startsWith( "cpu " ) ) //$NON-NLS-1$
			{
				line = line.substring( 3 ).trim( );

				StringTokenizer tokens = new StringTokenizer( line );

				long totaltime = 0, idletime = 0;
				int i = 0;
				String tk;

				while ( tokens.hasMoreTokens( ) && i < 7 )
				{
					tk = tokens.nextToken( );

					if ( i == 3 )
					{
						idletime = Long.parseLong( tk );
					}
					else
					{
						totaltime += Long.parseLong( tk );
					}
					i++;
				}

				return new long[]{
						totaltime, idletime
				};
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

		return null;
	}

	private static void readProcessStat( Context ctx, byte[] buf,
			ProcessItem pi, boolean showMem, boolean showCpu )
	{
		InputStream is = null;
		try
		{
			is = new FileInputStream( "/proc/" //$NON-NLS-1$
					+ pi.procInfo.pid
					+ "/stat" ); //$NON-NLS-1$

			ByteArrayOutputStream output = new ByteArrayOutputStream( );

			int len;

			while ( ( len = is.read( buf ) ) != -1 )
			{
				output.write( buf, 0, len );
			}

			output.close( );

			String line = output.toString( );

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

					long nrss;
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

					if ( showCpu )
					{
						if ( utime != null )
						{
							pi.cputime = Long.parseLong( utime );
						}

						if ( stime != null )
						{
							pi.cputime += Long.parseLong( stime );
						}
					}

					if ( showMem && rss != null )
					{
						nrss = Long.parseLong( rss );

						if ( pi.rss != nrss || pi.mem == null )
						{
							pi.rss = nrss;

							pi.mem = Formatter.formatFileSize( ctx,
									pi.rss * 4 * 1024 );
						}
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
			if ( is != null )
			{
				try
				{
					is.close( );
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

	/**
	 * @return [State, UID, GID, Threads]
	 */
	private static String[] readProcStatus( int pid )
	{
		BufferedReader reader = null;

		try
		{
			reader = new BufferedReader( new InputStreamReader( new FileInputStream( "/proc/" //$NON-NLS-1$
					+ pid
					+ "/status" ) ), //$NON-NLS-1$
					1024 );

			String line;
			String stateMsg = ""; //$NON-NLS-1$
			String uidMsg = ""; //$NON-NLS-1$
			String gidMsg = ""; //$NON-NLS-1$
			String threadsMsg = ""; //$NON-NLS-1$

			while ( ( line = reader.readLine( ) ) != null )
			{
				if ( line.startsWith( "State:" ) ) //$NON-NLS-1$
				{
					if ( line.length( ) > 6 )
					{
						stateMsg = line.substring( 6 ).trim( );
					}
				}
				else if ( line.startsWith( "Uid:" ) ) //$NON-NLS-1$
				{
					if ( line.length( ) > 4 )
					{
						uidMsg = line.substring( 4 ).trim( );

						int idx = uidMsg.indexOf( '\t' );
						if ( idx != -1 )
						{
							uidMsg = uidMsg.substring( 0, idx );
						}
						else
						{
							idx = uidMsg.indexOf( ' ' );
							if ( idx != -1 )
							{
								uidMsg = uidMsg.substring( 0, idx );
							}
						}
					}
				}
				else if ( line.startsWith( "Gid:" ) ) //$NON-NLS-1$
				{
					if ( line.length( ) > 4 )
					{
						gidMsg = line.substring( 4 ).trim( );

						int idx = gidMsg.indexOf( '\t' );
						if ( idx != -1 )
						{
							gidMsg = gidMsg.substring( 0, idx );
						}
						else
						{
							idx = gidMsg.indexOf( ' ' );
							if ( idx != -1 )
							{
								gidMsg = gidMsg.substring( 0, idx );
							}
						}
					}
				}
				else if ( line.startsWith( "Threads:" ) ) //$NON-NLS-1$
				{
					if ( line.length( ) > 8 )
					{
						threadsMsg = line.substring( 8 ).trim( );
					}
				}
			}

			return new String[]{
					stateMsg, uidMsg, gidMsg, threadsMsg
			};
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
				catch ( IOException ie )
				{
					Log.e( ProcessManager.class.getName( ),
							ie.getLocalizedMessage( ),
							ie );
				}
			}
		}

		return null;
	}

	/**
	 * ResourceUpdaterThread
	 */
	private static final class ResourceUpdaterThread extends Thread
	{

		private Activity ac;
		private ProcessCache procCache;
		private Handler handler;

		volatile boolean aborted;

		ResourceUpdaterThread( Activity ac, ProcessCache procCache,
				Handler handler )
		{
			super( "ProcessResourceUpdater" ); //$NON-NLS-1$

			this.ac = ac;
			this.procCache = procCache;
			this.handler = handler;
		}

		public void run( )
		{
			PackageManager pm = ac.getPackageManager( );

			boolean changed = false;

			ArrayList<ProcessItem> localList = procCache.generateLocalList( );

			for ( int i = 0, size = localList.size( ); i < size; i++ )
			{
				if ( aborted )
				{
					return;
				}

				ProcessItem proc = localList.get( i );

				String pname = proc.procInfo.processName;

				if ( procCache.resCache.containsKey( pname ) )
				{
					continue;
				}

				try
				{
					ApplicationInfo ai = pm.getApplicationInfo( pname, 0 );

					if ( ai != null )
					{
						CharSequence label = pm.getApplicationLabel( ai );

						if ( label != null )
						{
							proc.label = label.toString( );

							changed = true;
						}
					}
				}
				catch ( NameNotFoundException e )
				{
					int idx = pname.indexOf( ':' );

					if ( idx != -1 )
					{
						String name = pname.substring( 0, idx );

						try
						{
							ApplicationInfo ai = pm.getApplicationInfo( name, 0 );

							if ( ai != null )
							{
								CharSequence label = pm.getApplicationLabel( ai );

								if ( label != null )
								{
									proc.label = label.toString( )
											+ pname.substring( idx );

									changed = true;
								}
							}
						}
						catch ( NameNotFoundException e1 )
						{
							// ignore this exception
						}
					}
				}

				procCache.resCache.put( pname, proc );
			}

			if ( changed )
			{
				// reorder by new names
				if ( Util.getIntOption( ac,
						PREF_KEY_SORT_ORDER_TYPE,
						ORDER_TYPE_NAME ) == ORDER_TYPE_NAME )
				{
					procCache.reOrder( ORDER_TYPE_NAME, Util.getIntOption( ac,
							PREF_KEY_SORT_DIRECTION,
							ORDER_ASC ) );

					handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_LABEL,
							1,
							0 ) );
				}
				else
				{
					handler.sendMessage( handler.obtainMessage( MSG_REFRESH_PKG_LABEL,
							0,
							0 ) );
				}
			}

			changed = false;

			for ( int i = 0, size = localList.size( ); i < size; i++ )
			{
				if ( aborted )
				{
					return;
				}

				ProcessItem proc = localList.get( i );

				String pname = proc.procInfo.processName;

				if ( proc.icon != null )
				{
					continue;
				}

				try
				{
					ApplicationInfo ai = pm.getApplicationInfo( pname, 0 );

					if ( ai != null )
					{
						try
						{
							proc.icon = pm.getApplicationIcon( ai );

							changed = true;
						}
						catch ( OutOfMemoryError oom )
						{
							Log.e( ProcessManager.class.getName( ),
									"OOM when loading icon: " //$NON-NLS-1$
											+ ai.packageName,
									oom );
						}
					}
				}
				catch ( NameNotFoundException e1 )
				{
					// ignore this exception
				}
			}

			if ( changed )
			{
				handler.sendEmptyMessage( MSG_REFRESH_PKG_ICON );
			}
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

			CheckBoxPreference perfShowMem = new CheckBoxPreference( this );
			perfShowMem.setKey( PREF_KEY_SHOW_MEM );
			perfShowMem.setTitle( R.string.show_memory_usage );
			perfShowMem.setSummary( R.string.show_memory_summary );
			pc.addPreference( perfShowMem );

			CheckBoxPreference perfShowCpu = new CheckBoxPreference( this );
			perfShowCpu.setKey( PREF_KEY_SHOW_CPU );
			perfShowCpu.setTitle( R.string.show_cpu_usage );
			perfShowCpu.setSummary( R.string.show_cpu_summary );
			pc.addPreference( perfShowCpu );

			CheckBoxPreference perfShowSys = new CheckBoxPreference( this );
			perfShowSys.setKey( PREF_KEY_SHOW_SYS_PROC );
			perfShowSys.setTitle( R.string.show_sys_process );
			perfShowSys.setSummary( R.string.show_sys_process_sum );
			pc.addPreference( perfShowSys );

			CheckBoxPreference perfKillWarn = new CheckBoxPreference( this );
			perfKillWarn.setKey( PREF_KEY_SHOW_KILL_WARN );
			perfKillWarn.setTitle( R.string.end_task_warning );
			perfKillWarn.setSummary( R.string.end_task_warning_sum );
			pc.addPreference( perfKillWarn );

			Preference perfDefaultAction = new Preference( this );
			perfDefaultAction.setKey( PREF_KEY_DEFAULT_TAP_ACTION );
			perfDefaultAction.setTitle( R.string.default_tap_action );
			pc.addPreference( perfDefaultAction );

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
			refreshBooleanOption( PREF_KEY_SHOW_MEM );
			refreshBooleanOption( PREF_KEY_SHOW_CPU );
			refreshBooleanOption( PREF_KEY_SHOW_SYS_PROC );
			refreshBooleanOption( PREF_KEY_SHOW_KILL_WARN );
			refreshDefaultAction( );
			refreshSortType( );
			refreshSortDirection( );
			refreshIgnoreAction( );
			refreshIgnoreList( );

			setResult( RESULT_OK, getIntent( ) );
		}

		void refreshInterval( )
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

		void refreshBooleanOption( String key )
		{
			boolean val = getIntent( ).getBooleanExtra( key, true );

			( (CheckBoxPreference) findPreference( key ) ).setChecked( val );
		}

		void refreshDefaultAction( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_DEFAULT_TAP_ACTION,
					ACTION_MENU );

			String label = null;
			switch ( type )
			{
				case ACTION_END :
					label = getString( R.string.end_task );
					break;
				case ACTION_END_OTHERS :
					label = getString( R.string.end_others );
					break;
				case ACTION_SWITCH :
					label = getString( R.string.switch_to );
					break;
				case ACTION_IGNORE :
					label = getString( R.string.ignore );
					break;
				case ACTION_DETAILS :
					label = getString( R.string.details );
					break;
				case ACTION_MENU :
					label = getString( R.string.show_menu );
					break;
			}

			findPreference( PREF_KEY_DEFAULT_TAP_ACTION ).setSummary( label );
		}

		void refreshSortType( )
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

		void refreshSortDirection( )
		{
			int type = getIntent( ).getIntExtra( PREF_KEY_SORT_DIRECTION,
					ORDER_ASC );

			String label = type == ORDER_ASC ? getString( R.string.ascending )
					: getString( R.string.descending );

			findPreference( PREF_KEY_SORT_DIRECTION ).setSummary( label );
		}

		void refreshIgnoreAction( )
		{
			int action = getIntent( ).getIntExtra( PREF_KEY_IGNORE_ACTION,
					IGNORE_ACTION_HIDDEN );

			findPreference( PREF_KEY_IGNORE_ACTION ).setSummary( action == IGNORE_ACTION_HIDDEN ? R.string.hidden
					: R.string.protect );
		}

		void refreshIgnoreList( )
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

		private static String getProcessLabel( String name, PackageManager pm )
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

			final String prefKey = preference.getKey( );

			if ( PREF_KEY_REFRESH_INTERVAL.equals( prefKey ) )
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
			else if ( PREF_KEY_SHOW_MEM.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_MEM,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_MEM ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_CPU.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_CPU,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_CPU ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_SYS_PROC.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_SYS_PROC,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_SYS_PROC ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_SHOW_KILL_WARN.equals( prefKey ) )
			{
				it.putExtra( PREF_KEY_SHOW_KILL_WARN,
						( (CheckBoxPreference) findPreference( PREF_KEY_SHOW_KILL_WARN ) ).isChecked( ) );

				return true;
			}
			else if ( PREF_KEY_DEFAULT_TAP_ACTION.equals( prefKey ) )
			{
				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						it.putExtra( PREF_KEY_DEFAULT_TAP_ACTION, which );

						dialog.dismiss( );

						refreshDefaultAction( );
					}
				};

				new AlertDialog.Builder( this ).setTitle( R.string.default_tap_action )
						.setNeutralButton( R.string.close, null )
						.setSingleChoiceItems( new String[]{
								getString( R.string.show_menu ),
								getString( R.string.switch_to ),
								getString( R.string.end_task ),
								getString( R.string.end_others ),
								getString( R.string.ignore ),
								getString( R.string.details ),
						},
								it.getIntExtra( PREF_KEY_DEFAULT_TAP_ACTION,
										ACTION_MENU ),
								listener )
						.create( )
						.show( );

				return true;
			}
			else if ( PREF_KEY_SORT_ORDER_TYPE.equals( prefKey ) )
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
			else if ( PREF_KEY_SORT_DIRECTION.equals( prefKey ) )
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
			else if ( PREF_KEY_IGNORE_ACTION.equals( prefKey ) )
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
			else if ( PREF_KEY_IGNORE_LIST.equals( prefKey ) )
			{
				final ArrayList<String> list = it.getStringArrayListExtra( PREF_KEY_IGNORE_LIST );
				final boolean[] state = new boolean[list.size( )];

				OnClickListener listener = new OnClickListener( ) {

					public void onClick( DialogInterface dialog, int which )
					{
						ArrayList<String> nlist = new ArrayList<String>( );

						for ( int i = 0, size = list.size( ); i < size; i++ )
						{
							if ( !state[i] )
							{
								nlist.add( list.get( i ) );
							}
						}

						if ( list.size( ) == nlist.size( ) )
						{
							Util.shortToast( ProcessSettings.this,
									R.string.no_item_remove );
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

				for ( int i = 0, size = list.size( ); i < size; i++ )
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

		boolean sys;

		long rss;

		String mem;

		long cputime;

		long lastcputime;

		ProcessItem( )
		{

		}

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

		HashMap<String, ProcessItem> resCache;

		ArrayList<ProcessItem> procList;

		ProcessCache( )
		{
			resCache = new HashMap<String, ProcessItem>( );
			procList = new ArrayList<ProcessItem>( );
		}

		synchronized void clear( )
		{
			resCache.clear( );
			procList.clear( );
		}

		synchronized ArrayList<ProcessItem> generateLocalList( )
		{
			ArrayList<ProcessItem> local = new ArrayList<ProcessItem>( );

			local.addAll( procList );

			return local;
		}

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
							return ( obj1.rss == obj2.rss ? 0
									: ( obj1.rss < obj2.rss ? -1 : 1 ) )
									* direction;
						}
					} );
					break;
				case ORDER_TYPE_CPU :
					Collections.sort( procList, new Comparator<ProcessItem>( ) {

						public int compare( ProcessItem obj1, ProcessItem obj2 )
						{
							long c1 = obj1.lastcputime == 0 ? 0
									: ( obj1.cputime - obj1.lastcputime );
							long c2 = obj2.lastcputime == 0 ? 0
									: ( obj2.cputime - obj2.lastcputime );
							return ( c1 == c2 ? 0 : ( c1 < c2 ? -1 : 1 ) )
									* direction;
						}
					} );
					break;
			}
		}
	}
}
