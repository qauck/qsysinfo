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

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ListActivity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

	private RunningAppProcessInfo dummyInfo;
	private ListView lstProcs;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		lstProcs = getListView( );

		registerForContextMenu( lstProcs );

		dummyInfo = new RunningAppProcessInfo( ) {

			{
				this.processName = ProcessManager.this.getString( R.string.end_proc_hint );
			}
		};

		lstProcs.setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				RunningAppProcessInfo rap = (RunningAppProcessInfo) parent.getItemAtPosition( position );

				if ( rap == dummyInfo )
				{
					endAll( );
				}
				else
				{
					ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

					endProcess( am, rap.pkgList );

					refresh( );
				}
			}
		} );
	}

	@Override
	protected void onResume( )
	{
		super.onResume( );

		refresh( );
	}

	@Override
	public void onCreateContextMenu( ContextMenu menu, View v,
			ContextMenuInfo menuInfo )
	{
		super.onCreateContextMenu( menu, v, menuInfo );

		int pos = ( (AdapterContextMenuInfo) menuInfo ).position;
		RunningAppProcessInfo rap = (RunningAppProcessInfo) lstProcs.getItemAtPosition( pos );

		if ( rap != dummyInfo )
		{
			menu.setHeaderTitle( R.string.actions );
			menu.add( Menu.NONE, MI_DISPLAY, MI_DISPLAY, R.string.switch_to );
			menu.add( Menu.NONE, MI_ENDTASK, MI_ENDTASK, R.string.end_task );
		}
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == MI_DISPLAY )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;
			RunningAppProcessInfo rap = (RunningAppProcessInfo) lstProcs.getItemAtPosition( pos );

			Intent it = new Intent( "android.intent.action.MAIN" ); //$NON-NLS-1$
			it.addCategory( Intent.CATEGORY_LAUNCHER );

			List<ResolveInfo> acts = getPackageManager( ).queryIntentActivities( it,
					0 );

			if ( acts != null )
			{
				String pkgName = rap.processName;
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

			return true;
		}
		else if ( item.getItemId( ) == MI_ENDTASK )
		{
			int pos = ( (AdapterContextMenuInfo) item.getMenuInfo( ) ).position;
			RunningAppProcessInfo rap = (RunningAppProcessInfo) lstProcs.getItemAtPosition( pos );

			ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

			endProcess( am, rap.pkgList );

			refresh( );

			return true;
		}

		return super.onContextItemSelected( item );
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

		for ( int i = 0; i < lstProcs.getCount( ); i++ )
		{
			RunningAppProcessInfo rap = (RunningAppProcessInfo) lstProcs.getItemAtPosition( i );

			if ( !self.equals( rap.processName ) )
			{
				endProcess( am, rap.pkgList );
			}
		}

		am.restartPackage( self );
	}

	private void refresh( )
	{
		ActivityManager am = (ActivityManager) this.getSystemService( ACTIVITY_SERVICE );

		final PackageManager pm = getPackageManager( );

		List<RunningAppProcessInfo> procs = am.getRunningAppProcesses( );

		procs = filter( procs );

		ArrayAdapter<RunningAppProcessInfo> adapter = new ArrayAdapter<RunningAppProcessInfo>( this,
				R.layout.proc_item,
				procs.toArray( new RunningAppProcessInfo[procs.size( )] ) ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name;
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

				RunningAppProcessInfo itm = getItem( position );

				txt_name = (TextView) view.findViewById( R.id.txt_proc_name );
				txt_name.setText( itm.processName );

				img_type = (ImageView) view.findViewById( R.id.img_proc_icon );

				if ( itm == dummyInfo )
				{
					if ( txt_name.getTypeface( ) == null
							|| txt_name.getTypeface( ).getStyle( ) != Typeface.ITALIC )
					{
						txt_name.setTypeface( Typeface.DEFAULT, Typeface.ITALIC );
					}

					txt_name.setTextColor( Color.WHITE );

					img_type.setImageDrawable( null );
				}
				else
				{
					if ( txt_name.getTypeface( ) == null
							|| txt_name.getTypeface( ).getStyle( ) != Typeface.NORMAL )
					{
						txt_name.setTypeface( Typeface.DEFAULT, Typeface.NORMAL );
					}

					switch ( itm.importance )
					{
						case RunningAppProcessInfo.IMPORTANCE_SERVICE :
							txt_name.setTextColor( Color.GRAY );
							break;
						case RunningAppProcessInfo.IMPORTANCE_BACKGROUND :
							txt_name.setTextColor( Color.YELLOW );
							break;
						default :
							txt_name.setTextColor( Color.WHITE );
					}

					try
					{
						ApplicationInfo ai = pm.getApplicationInfo( itm.processName,
								0 );

						if ( ai != null )
						{
							CharSequence label = pm.getApplicationLabel( ai );

							Drawable icon = pm.getApplicationIcon( ai );

							if ( label != null )
							{
								txt_name.setText( label );
							}

							if ( icon == null )
							{
								icon = pm.getDefaultActivityIcon( );
							}

							img_type.setImageDrawable( icon );
						}
						else
						{
							img_type.setImageDrawable( pm.getDefaultActivityIcon( ) );
						}
					}
					catch ( NameNotFoundException e )
					{
						// just ignore this exception
					}
				}

				return view;
			}
		};

		lstProcs.setAdapter( adapter );
	}

	private List<RunningAppProcessInfo> filter( List<RunningAppProcessInfo> list )
	{
		ArrayList<RunningAppProcessInfo> procs = new ArrayList<RunningAppProcessInfo>( );

		procs.add( dummyInfo );

		if ( list != null )
		{
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

				procs.add( rap );
			}
		}

		return procs;
	}
}
