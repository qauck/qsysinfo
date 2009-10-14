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

package org.uguess.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

/**
 * ApplicationManager
 */
public class ApplicationManager extends ListActivity
{

	private static final int MSG_COPING = 1;
	private static final int MSG_COPING_ERROR = 2;
	private static final int MSG_COPING_FINISHED = 3;
	private static final int MSG_DISMISS_PROGRESS = 10;
	private static final int MSG_REFRESH_PKG_SIZE = 11;
	private static final int MSG_REFRESH_PKG_LABEL = 12;
	private static final int MSG_REFRESH_PKG_ICON = 13;

	private static final int DLG_WARNING = 1;

	private static final String EXPORT_FOLDER = "/sdcard/backups/"; //$NON-NLS-1$

	private static Method mdGetPackageSizeInfo;

	static
	{
		try
		{
			mdGetPackageSizeInfo = PackageManager.class.getMethod( "getPackageSizeInfo", //$NON-NLS-1$
					String.class,
					IPackageStatsObserver.class );
		}
		catch ( Exception e )
		{
			e.printStackTrace( );
		}
	}

	private ListView lstApps;

	private CopyHandler handler = new CopyHandler( );

	private ProgressDialog progress;

	private Drawable defaultIcon;

	private OnCheckedChangeListener checkListener = new OnCheckedChangeListener( ) {

		public void onCheckedChanged( CompoundButton buttonView,
				boolean isChecked )
		{
			( (AppInfoHolder) lstApps.getItemAtPosition( (Integer) buttonView.getTag( ) ) ).checked = isChecked ? Boolean.TRUE
					: null;
		}
	};

	@Override
	protected void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		defaultIcon = getResources( ).getDrawable( R.drawable.icon );

		lstApps = getListView( );

		lstApps.setOnItemClickListener( new OnItemClickListener( ) {

			public void onItemClick( AdapterView<?> parent, View view,
					int position, long id )
			{
				AppInfoHolder holder = (AppInfoHolder) parent.getItemAtPosition( position );

				Intent intent = new Intent( Intent.ACTION_VIEW );

				intent.setClassName( "com.android.settings", //$NON-NLS-1$
						"com.android.settings.InstalledAppDetails" ); //$NON-NLS-1$
				intent.putExtra( "com.android.settings.ApplicationPkgName", //$NON-NLS-1$
						holder.appInfo.packageName );

				startActivity( intent );
			}
		} );
	}

	@Override
	protected void onStart( )
	{
		super.onStart( );

		loadApps( );
	}

	@Override
	protected Dialog onCreateDialog( int id )
	{
		if ( id == DLG_WARNING )
		{
			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					if ( which == Dialog.BUTTON_POSITIVE )
					{
						exportSelected( );
					}
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setTitle( R.string.warning );
			builder.setMessage( MessageFormat.format( getResources( ).getString( R.string.warning_msg ),
					EXPORT_FOLDER ) );

			builder.setPositiveButton( R.string.cont, listener );

			builder.setNegativeButton( R.string.cancel, listener );

			return builder.create( );
		}
		return super.onCreateDialog( id );
	}

	private void loadApps( )
	{
		final PackageManager pm = getPackageManager( );
		final List<ApplicationInfo> apps = pm.getInstalledApplications( 0 );

		Collections.sort( apps, new ApplicationInfo.DisplayNameComparator( pm ) );

		ArrayList<AppInfoHolder> dataList = new ArrayList<AppInfoHolder>( );

		for ( Iterator<ApplicationInfo> itr = apps.iterator( ); itr.hasNext( ); )
		{
			AppInfoHolder holder = new AppInfoHolder( );
			holder.appInfo = itr.next( );

			dataList.add( holder );
		}

		ArrayAdapter<AppInfoHolder> adapter = new ArrayAdapter<AppInfoHolder>( this,
				R.layout.app_item,
				dataList ) {

			public android.view.View getView( int position,
					android.view.View convertView, android.view.ViewGroup parent )
			{
				View view;
				TextView txt_name, txt_size;
				ImageView img_type;
				CheckBox ckb_app;

				if ( convertView == null )
				{
					view = ApplicationManager.this.getLayoutInflater( )
							.inflate( R.layout.app_item, parent, false );
				}
				else
				{
					view = convertView;
				}

				AppInfoHolder itm = getItem( position );

				txt_name = (TextView) view.findViewById( R.id.app_name );
				if ( itm.label != null )
				{
					txt_name.setText( itm.label );
				}
				else
				{
					txt_name.setText( itm.appInfo.packageName );
				}

				txt_size = (TextView) view.findViewById( R.id.app_size );
				if ( itm.size != null )
				{
					txt_size.setText( itm.size );
				}
				else
				{
					txt_size.setText( R.string.computing );
				}

				img_type = (ImageView) view.findViewById( R.id.img_app_icon );
				if ( itm.icon != null )
				{
					img_type.setImageDrawable( itm.icon );
				}
				else
				{
					img_type.setImageDrawable( defaultIcon );
				}

				ckb_app = (CheckBox) view.findViewById( R.id.ckb_app );
				ckb_app.setTag( position );
				ckb_app.setChecked( itm.checked != null
						&& itm.checked.booleanValue( ) );
				ckb_app.setOnCheckedChangeListener( checkListener );

				return view;
			}
		};

		lstApps.setAdapter( adapter );

		new Thread( new Runnable( ) {

			public void run( )
			{
				for ( int i = 0; i < apps.size( ); i++ )
				{
					invokeGetPkgSize( i, apps.get( i ).packageName, pm );
				}

			}
		} ).start( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				ArrayList<CharSequence> labels = new ArrayList<CharSequence>( );
				for ( int i = 0; i < apps.size( ); i++ )
				{
					CharSequence label = apps.get( i ).loadLabel( pm );

					labels.add( label );
				}

				Message msg = handler.obtainMessage( MSG_REFRESH_PKG_LABEL );
				msg.obj = labels;
				handler.sendMessage( msg );

				ArrayList<Drawable> icons = new ArrayList<Drawable>( );
				for ( int i = 0; i < apps.size( ); i++ )
				{
					Drawable icon = apps.get( i ).loadIcon( pm );

					icons.add( icon );
				}

				msg = handler.obtainMessage( MSG_REFRESH_PKG_ICON );
				msg.obj = icons;
				handler.sendMessage( msg );
			}
		} ).start( );
	}

	private void invokeGetPkgSize( int idx, String pkgName, PackageManager pm )
	{
		if ( mdGetPackageSizeInfo != null )
		{
			try
			{
				mdGetPackageSizeInfo.invoke( pm,
						pkgName,
						new PkgSizeObserver( idx ) );
			}
			catch ( Exception e )
			{
				e.printStackTrace( );
			}
		}
	}

	private void refreshPkgSize( int idx )
	{
		int count = lstApps.getChildCount( );

		for ( int i = 0; i < count; i++ )
		{
			ViewGroup vg = (ViewGroup) lstApps.getChildAt( i );

			CheckBox ckb_app = (CheckBox) vg.findViewById( R.id.ckb_app );
			int pos = ( (Integer) ckb_app.getTag( ) ).intValue( );

			if ( pos == idx )
			{
				String size = ( (AppInfoHolder) lstApps.getItemAtPosition( idx ) ).size;

				if ( size != null )
				{
					TextView txt_size = (TextView) vg.findViewById( R.id.app_size );

					txt_size.setText( size );
				}
				break;
			}
		}
	}

	private void refreshPkgLabel( ArrayList<CharSequence> labels )
	{
		for ( int i = 0; i < labels.size( ); i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			holder.label = labels.get( i );
		}

		int count = lstApps.getChildCount( );

		for ( int i = 0; i < count; i++ )
		{
			ViewGroup vg = (ViewGroup) lstApps.getChildAt( i );

			CheckBox ckb_app = (CheckBox) vg.findViewById( R.id.ckb_app );
			int pos = ( (Integer) ckb_app.getTag( ) ).intValue( );

			CharSequence label = labels.get( pos );

			if ( label != null )
			{
				TextView txt_name = (TextView) vg.findViewById( R.id.app_name );

				txt_name.setText( label );
			}
		}
	}

	private void refreshPkgIcon( ArrayList<Drawable> icons )
	{
		for ( int i = 0; i < icons.size( ); i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			holder.icon = icons.get( i );
		}

		int count = lstApps.getChildCount( );

		for ( int i = 0; i < count; i++ )
		{
			ViewGroup vg = (ViewGroup) lstApps.getChildAt( i );

			CheckBox ckb_app = (CheckBox) vg.findViewById( R.id.ckb_app );
			int pos = ( (Integer) ckb_app.getTag( ) ).intValue( );

			Drawable icon = icons.get( pos );

			if ( icon != null )
			{
				ImageView img_icon = (ImageView) vg.findViewById( R.id.img_app_icon );

				img_icon.setImageDrawable( icon );
			}
		}
	}

	private boolean ensureSDCard( )
	{
		String state = Environment.getExternalStorageState( );

		return Environment.MEDIA_MOUNTED.equals( state );
	}

	private void exportSelected( )
	{
		int count = lstApps.getCount( );

		ArrayList<ApplicationInfo> apps = new ArrayList<ApplicationInfo>( );

		for ( int i = 0; i < count; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			if ( holder.checked != null && holder.checked.booleanValue( ) )
			{
				apps.add( holder.appInfo );
			}
		}

		export( apps );
	}

	private void export( final List<ApplicationInfo> apps )
	{
		if ( apps == null || apps.isEmpty( ) )
		{
			Toast.makeText( this, R.string.no_app_selected, Toast.LENGTH_SHORT )
					.show( );
			return;
		}

		if ( progress == null )
		{
			progress = new ProgressDialog( this );
		}
		progress.setMessage( getResources( ).getString( R.string.start_exporting ) );
		progress.setIndeterminate( false );
		progress.setProgressStyle( ProgressDialog.STYLE_HORIZONTAL );
		progress.setMax( apps.size( ) );

		progress.show( );

		new Thread( new Runnable( ) {

			public void run( )
			{
				File output = new File( EXPORT_FOLDER );

				if ( !output.exists( ) )
				{
					output.mkdirs( );
				}

				for ( int i = 0; i < apps.size( ); i++ )
				{
					ApplicationInfo app = apps.get( i );

					String src = app.sourceDir;

					if ( src != null )
					{
						File srcFile = new File( src );

						String appName = getFileName( src );

						if ( appName != null )
						{
							File destFile = new File( output, appName );

							handler.sendMessage( Message.obtain( handler,
									MSG_COPING,
									appName ) );

							try
							{
								copyFile( srcFile, destFile );
								// Thread.sleep( 500 );
							}
							catch ( Exception e )
							{
								handler.sendMessage( Message.obtain( handler,
										MSG_COPING_ERROR,
										e ) );
							}
						}
					}
				}

				handler.sendMessage( Message.obtain( handler,
						MSG_COPING_FINISHED,
						apps.size( ) ) );
			}
		} ).start( );
	}

	private String getFileName( String fullName )
	{
		if ( fullName == null )
		{
			return null;
		}

		int idx = fullName.lastIndexOf( '/' );
		if ( idx == -1 )
		{
			return fullName;
		}

		return fullName.substring( idx + 1 );
	}

	private void copyFile( File src, File dest ) throws IOException
	{
		InputStream fis = new BufferedInputStream( new FileInputStream( src ) );
		OutputStream fos = new BufferedOutputStream( new FileOutputStream( dest ) );

		byte[] buf = new byte[4096];

		int i;
		while ( ( i = fis.read( buf ) ) != -1 )
		{
			fos.write( buf, 0, i );
		}

		fis.close( );
		fos.close( );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuInflater inflater = getMenuInflater( );
		inflater.inflate( R.menu.apps_options, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		if ( item.getItemId( ) == R.id.mi_export )
		{
			if ( !ensureSDCard( ) )
			{
				Toast.makeText( this, R.string.sd_error, Toast.LENGTH_SHORT )
						.show( );
			}
			else
			{
				showDialog( DLG_WARNING );
			}
			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_select_all )
		{
			toggleAllSelection( true );

			return true;
		}
		else if ( item.getItemId( ) == R.id.mi_deselect_all )
		{
			toggleAllSelection( false );

			return true;
		}

		return false;
	}

	private void toggleAllSelection( boolean selected )
	{
		// reset visible view item states
		int count = lstApps.getChildCount( );
		for ( int i = 0; i < count; i++ )
		{
			ViewGroup vg = (ViewGroup) lstApps.getChildAt( i );

			CheckBox ckb_app = (CheckBox) vg.findViewById( R.id.ckb_app );
			ckb_app.setChecked( selected );
		}

		// reset hidden item states
		int totalCount = lstApps.getCount( );
		for ( int i = 0; i < totalCount; i++ )
		{
			AppInfoHolder holder = (AppInfoHolder) lstApps.getItemAtPosition( i );

			holder.checked = selected ? Boolean.TRUE : null;
		}
	}

	/**
	 * CopyHandler
	 */
	class CopyHandler extends Handler
	{

		@Override
		public void handleMessage( Message msg )
		{
			AppInfoHolder holder;

			switch ( msg.what )
			{
				case MSG_COPING :

					if ( progress != null )
					{
						progress.setMessage( MessageFormat.format( getResources( ).getString( R.string.exporting ),
								msg.obj ) );
						progress.setProgress( progress.getProgress( ) + 1 );
					}
					break;
				case MSG_COPING_ERROR :

					Toast.makeText( ApplicationManager.this,
							MessageFormat.format( getResources( ).getString( R.string.copy_error ),
									( (Exception) msg.obj ).getLocalizedMessage( ) ),
							Toast.LENGTH_LONG )
							.show( );
					break;
				case MSG_COPING_FINISHED :

					if ( progress != null )
					{
						progress.setMessage( MessageFormat.format( getResources( ).getString( R.string.exported ),
								msg.obj ) );
						progress.setProgress( progress.getMax( ) );
						progress.dismiss( );
						progress = null;
					}

					Toast.makeText( ApplicationManager.this,
							MessageFormat.format( getResources( ).getString( R.string.exported_to ),
									msg.obj,
									EXPORT_FOLDER ),
							Toast.LENGTH_SHORT )
							.show( );

					Notification nc = new Notification( R.drawable.icon,
							getResources( ).getString( R.string.export_complete ),
							System.currentTimeMillis( ) );

					PendingIntent pit = PendingIntent.getActivity( ApplicationManager.this,
							0,
							new Intent( ),
							0 );

					nc.flags |= Notification.FLAG_AUTO_CANCEL;
					nc.setLatestEventInfo( ApplicationManager.this,
							getResources( ).getString( R.string.export_complete ),
							MessageFormat.format( getResources( ).getString( R.string.exported ),
									msg.obj ),
							pit );

					( (NotificationManager) getSystemService( NOTIFICATION_SERVICE ) ).notify( MSG_COPING_FINISHED,
							nc );
					break;
				case MSG_DISMISS_PROGRESS :

					if ( progress != null )
					{
						progress.dismiss( );
						progress = null;
					}
					break;
				case MSG_REFRESH_PKG_SIZE :

					PackageStats ps = (PackageStats) msg.obj;
					holder = (AppInfoHolder) lstApps.getItemAtPosition( msg.arg1 );
					holder.size = Formatter.formatFileSize( ApplicationManager.this,
							ps.codeSize )
							+ " + " //$NON-NLS-1$
							+ Formatter.formatFileSize( ApplicationManager.this,
									ps.dataSize );

					refreshPkgSize( msg.arg1 );
					break;
				case MSG_REFRESH_PKG_LABEL :

					refreshPkgLabel( (ArrayList<CharSequence>) msg.obj );
					break;
				case MSG_REFRESH_PKG_ICON :

					refreshPkgIcon( (ArrayList<Drawable>) msg.obj );
					break;
			}
		}
	}

	/**
	 * PackageSizeObserver
	 */
	class PkgSizeObserver extends IPackageStatsObserver.Stub
	{

		int idx;

		PkgSizeObserver( int idx )
		{
			this.idx = idx;
		}

		public void onGetStatsCompleted( PackageStats pStats, boolean succeeded )
				throws RemoteException
		{
			Message msg = handler.obtainMessage( MSG_REFRESH_PKG_SIZE );
			msg.obj = pStats;
			msg.arg1 = idx;
			handler.sendMessage( msg );
		}

	}

	/**
	 * AppInfoHolder
	 */
	static class AppInfoHolder
	{

		ApplicationInfo appInfo;

		CharSequence label;

		Drawable icon;

		String size;

		Boolean checked;
	}

}
