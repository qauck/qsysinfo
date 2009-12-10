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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * ProcessManager
 */
public class ProcessManager extends ListActivity
{

	private RunningAppProcessInfo dmmyInfo;
	private ListView lstProcs;

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		lstProcs = getListView( );

		dmmyInfo = new RunningAppProcessInfo( ) {

			{
				this.processName = ProcessManager.this.getString( R.string.end_proc_hint );
			}
		};

		lstProcs.setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				RunningAppProcessInfo rap = (RunningAppProcessInfo) parent.getItemAtPosition( position );

				if ( rap == dmmyInfo )
				{
					endAll( );
				}
				else
				{
					ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

					am.restartPackage( rap.processName );

					refresh( );
				}
			}
		} );

		refresh( );
	}

	private void endAll( )
	{
		ActivityManager am = (ActivityManager) ProcessManager.this.getSystemService( ACTIVITY_SERVICE );

		for ( int i = 0; i < lstProcs.getCount( ); i++ )
		{
			RunningAppProcessInfo rap = (RunningAppProcessInfo) lstProcs.getItemAtPosition( i );

			am.restartPackage( rap.processName );
		}
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

				if ( itm == dmmyInfo )
				{
					if ( txt_name.getTypeface( ) == null
							|| txt_name.getTypeface( ).getStyle( ) != Typeface.ITALIC )
					{
						txt_name.setTypeface( Typeface.DEFAULT, Typeface.ITALIC );
					}

					img_type.setImageDrawable( null );
				}
				else
				{
					if ( txt_name.getTypeface( ) == null
							|| txt_name.getTypeface( ).getStyle( ) != Typeface.NORMAL )
					{
						txt_name.setTypeface( Typeface.DEFAULT, Typeface.NORMAL );
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
					}
					catch ( NameNotFoundException e )
					{
						Log.e( ProcessManager.class.getName( ),
								e.getLocalizedMessage( ),
								e );
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

		procs.add( dmmyInfo );

		if ( list != null )
		{
			String name;
			for ( RunningAppProcessInfo rap : list )
			{
				name = rap.processName;

				if ( name.contains( "com.google.process" ) //$NON-NLS-1$
						|| name.contains( "com.android.phone" ) //$NON-NLS-1$
						|| name.contains( "android.process" ) //$NON-NLS-1$
						|| name.contains( "system" ) //$NON-NLS-1$
						|| name.contains( "com.android.inputmethod" ) //$NON-NLS-1$
						|| name.contains( "com.android.alarmclock" ) ) //$NON-NLS-1$
				{
					continue;
				}

				procs.add( rap );
			}
		}

		return procs;
	}
}
