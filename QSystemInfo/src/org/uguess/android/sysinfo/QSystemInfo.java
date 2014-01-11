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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TabWidget;

/**
 * QSystemInfo
 */
public final class QSystemInfo extends FragmentActivity
{

	private static final String PREF_KEY_LAST_ACTIVE = "last_active_tab"; //$NON-NLS-1$

	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );

		requestWindowFeature( Window.FEATURE_NO_TITLE );

		Util.hookExceptionHandler( getApplicationContext( ) );

		setContentView( R.layout.main );

		TabHost th = (TabHost) findViewById( android.R.id.tabhost );
		th.setup( );

		ViewPager vp = (ViewPager) findViewById( R.id.pager );

		TabsAdapter tabAdapter = new TabsAdapter( this, th, vp );

		tabAdapter.addTab( th.newTabSpec( SysInfoManager.class.getName( ) )
				.setIndicator( getString( R.string.tab_info ),
						getResources( ).getDrawable( R.drawable.info ) ),
				SysInfoManager.class,
				null );

		tabAdapter.addTab( th.newTabSpec( ApplicationManager.class.getName( ) )
				.setIndicator( getString( R.string.tab_apps ),
						getResources( ).getDrawable( R.drawable.applications ) ),
				ApplicationManager.class,
				null );

		tabAdapter.addTab( th.newTabSpec( ProcessManager.class.getName( ) )
				.setIndicator( getString( R.string.tab_procs ),
						getResources( ).getDrawable( R.drawable.processes ) ),
				ProcessManager.class,
				null );

		tabAdapter.addTab( th.newTabSpec( NetStateManager.class.getName( ) )
				.setIndicator( getString( R.string.tab_netstat ),
						getResources( ).getDrawable( R.drawable.connection ) ),
				NetStateManager.class,
				null );

		SharedPreferences sp = getSharedPreferences( SysInfoManager.PSTORE_SYSINFOMANAGER,
				Context.MODE_PRIVATE );

		Util.updateIcons( this, sp );

		if ( sp != null )
		{
			int tab = sp.getInt( SysInfoManager.PREF_KEY_DEFAULT_TAB, 0 );

			if ( tab == 0 )
			{
				tab = sp.getInt( PREF_KEY_LAST_ACTIVE, 1 );
			}

			if ( tab > 0 && tab < 5 )
			{
				th.setCurrentTab( tab - 1 );
			}
		}
	}

	@Override
	protected void onDestroy( )
	{
		SharedPreferences sp = getSharedPreferences( SysInfoManager.PSTORE_SYSINFOMANAGER,
				Context.MODE_PRIVATE );

		if ( sp != null )
		{
			int tab = sp.getInt( SysInfoManager.PREF_KEY_DEFAULT_TAB, 0 );

			if ( tab == 0 )
			{
				Editor et = sp.edit( );
				et.putInt( PREF_KEY_LAST_ACTIVE,
						( (TabHost) findViewById( android.R.id.tabhost ) ).getCurrentTab( ) + 1 );
				et.commit( );
			}
		}

		super.onDestroy( );
	}

	/**
	 * TabInfo
	 */
	static final class TabInfo
	{

		private String tag;
		private Class<?> clss;
		private Bundle args;

		TabInfo( String _tag, Class<?> _class, Bundle _args )
		{
			tag = _tag;
			clss = _class;
			args = _args;
		}
	}

	static final class TabFactory implements TabHost.TabContentFactory
	{

		private Context mContext;

		public TabFactory( Context context )
		{
			mContext = context;
		}

		@Override
		public View createTabContent( String tag )
		{
			View v = new View( mContext );
			v.setMinimumWidth( 0 );
			v.setMinimumHeight( 0 );
			return v;
		}
	}

	/**
	 * TabsAdapter
	 */
	static final class TabsAdapter extends FragmentPagerAdapter implements
			TabHost.OnTabChangeListener,
			ViewPager.OnPageChangeListener
	{

		private Context mContext;
		private TabHost mTabHost;
		private ViewPager mViewPager;
		private ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>( );

		public TabsAdapter( FragmentActivity activity, TabHost tabHost,
				ViewPager pager )
		{
			super( activity.getSupportFragmentManager( ) );

			mContext = activity;
			mTabHost = tabHost;
			mViewPager = pager;

			mTabHost.setOnTabChangedListener( this );
			mViewPager.setAdapter( this );
			mViewPager.setOnPageChangeListener( this );
		}

		public void addTab( TabHost.TabSpec tabSpec, Class<?> clss, Bundle args )
		{
			tabSpec.setContent( new TabFactory( mContext ) );
			String tag = tabSpec.getTag( );

			TabInfo info = new TabInfo( tag, clss, args );
			mTabs.add( info );
			mTabHost.addTab( tabSpec );
			notifyDataSetChanged( );
		}

		@Override
		public int getCount( )
		{
			return mTabs.size( );
		}

		@Override
		public Fragment getItem( int position )
		{
			TabInfo info = mTabs.get( position );
			return Fragment.instantiate( mContext,
					info.clss.getName( ),
					info.args );
		}

		@Override
		public void onTabChanged( String tabId )
		{
			int position = mTabHost.getCurrentTab( );
			mViewPager.setCurrentItem( position );
		}

		@Override
		public void onPageScrolled( int position, float positionOffset,
				int positionOffsetPixels )
		{
		}

		@Override
		public void onPageSelected( int position )
		{
			// Unfortunately when TabHost changes the current tab, it kindly
			// also takes care of putting focus on it when not in touch mode.
			// The jerk.
			// This hack tries to prevent this from pulling focus out of our
			// ViewPager.
			TabWidget widget = mTabHost.getTabWidget( );
			int oldFocusability = widget.getDescendantFocusability( );
			widget.setDescendantFocusability( ViewGroup.FOCUS_BLOCK_DESCENDANTS );
			mTabHost.setCurrentTab( position );
			widget.setDescendantFocusability( oldFocusability );
		}

		@Override
		public void onPageScrollStateChanged( int state )
		{
		}
	}

	/**
	 * ErrorHandler
	 */
	static final class ErrorHandler implements
			UncaughtExceptionHandler,
			Constants
	{

		private UncaughtExceptionHandler parentHandler;
		private Context ctx;

		ErrorHandler( Context ctx, UncaughtExceptionHandler parentHandler )
		{
			this.parentHandler = parentHandler;
			this.ctx = ctx;
		}

		public void uncaughtException( Thread thread, Throwable ex )
		{
			Intent it = new Intent( Intent.ACTION_VIEW );
			it.setClass( ctx, ErrorReportActivity.class );
			it.setFlags( it.getFlags( )
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TOP );
			it.putExtra( "thread", thread.toString( ) ); //$NON-NLS-1$
			it.putExtra( "exception", Log.getStackTraceString( ex ) ); //$NON-NLS-1$

			PendingIntent pi = PendingIntent.getActivity( ctx, 0, it, 0 );

			Notification nc = new Notification( R.drawable.icon, "Oops", //$NON-NLS-1$
					System.currentTimeMillis( ) );

			nc.flags |= Notification.FLAG_AUTO_CANCEL;
			nc.setLatestEventInfo( ctx,
					ctx.getString( R.string.oops ),
					ctx.getString( R.string.oops_msg ),
					pi );

			( (NotificationManager) ctx.getSystemService( NOTIFICATION_SERVICE ) ).notify( NOTIFY_ERROR_REPORT,
					nc );

			if ( parentHandler != null )
			{
				parentHandler.uncaughtException( thread, ex );
			}
		}
	}

	/**
	 * ErrorReportActivity
	 */
	public static final class ErrorReportActivity extends Activity
	{

		@Override
		protected void onCreate( Bundle savedInstanceState )
		{
			super.onCreate( savedInstanceState );

			getWindow( ).setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN );

			Util.hookExceptionHandler( getApplicationContext( ) );

			OnClickListener listener = new OnClickListener( ) {

				public void onClick( DialogInterface dialog, int which )
				{
					if ( which == DialogInterface.BUTTON_POSITIVE )
					{
						sendBugReport( );
					}

					ErrorReportActivity.this.finish( );
				}
			};

			new AlertDialog.Builder( this ).setTitle( R.string.bug_title )
					.setMessage( R.string.bug_detail )
					.setPositiveButton( R.string.agree, listener )
					.setNegativeButton( android.R.string.no, listener )
					.setCancelable( false )
					.create( )
					.show( );
		}

		void sendBugReport( )
		{
			StringBuffer msg = new StringBuffer( );
			Intent it = new Intent( Intent.ACTION_SENDTO );
			String content = null;

			try
			{
				String title = "Bug Report - " + new Date( ).toLocaleString( ); //$NON-NLS-1$

				it.setData( Uri.parse( "mailto:qauck.aa@gmail.com" ) ); //$NON-NLS-1$

				it.putExtra( Intent.EXTRA_SUBJECT, title );

				SysInfoManager.createTextHeader( this, msg, title );

				msg.append( "\n-----THREAD-----\n" ) //$NON-NLS-1$
						.append( getIntent( ).getStringExtra( "thread" ) ); //$NON-NLS-1$

				msg.append( "\n\n-----EXCEPTION-----\n" ) //$NON-NLS-1$
						.append( getIntent( ).getStringExtra( "exception" ) );; //$NON-NLS-1$

				// try get the intermediate report first
				content = msg.toString( );

				msg.append( "\n\n-----LOGCAT-----\n" ); //$NON-NLS-1$

				Process proc = Runtime.getRuntime( )
						.exec( "logcat -d -v time *:V" ); //$NON-NLS-1$

				SysInfoManager.readRawText( msg, proc.getInputStream( ) );

				msg.append( "\n-----LOGCAT-END-----\n" ); //$NON-NLS-1$
			}
			catch ( Throwable e )
			{
				try
				{
					msg.append( "\n\n-----ERROR-COLLECT-REPORT-----\n" ); //$NON-NLS-1$
					msg.append( Log.getStackTraceString( e ) );
					msg.append( "\n-----ERROR-END-----\n" ); //$NON-NLS-1$
				}
				catch ( Throwable t )
				{
					// must be OOM, doing nothing
				}
			}
			finally
			{
				try
				{
					// get the final report
					content = msg.toString( );
				}
				catch ( Throwable t )
				{
					// mostly still be OOM, doing nothing
				}
				finally
				{
					if ( content != null )
					{
						try
						{
							it.putExtra( Intent.EXTRA_TEXT, content );

							it = Intent.createChooser( it, null );
							startActivity( it );

							return;
						}
						catch ( Throwable t )
						{
							// failed at last stage, log and give up
							Log.e( getClass( ).getName( ),
									t.getLocalizedMessage( ),
									t );
						}
					}

					Util.shortToast( this, R.string.bug_failed );
				}
			}
		}
	}

	/**
	 * BootReceiver
	 */
	public static final class BootReceiver extends BroadcastReceiver
	{

		@Override
		public void onReceive( Context context, Intent intent )
		{
			SharedPreferences sp = context.getSharedPreferences( SysInfoManager.PSTORE_SYSINFOMANAGER,
					Context.MODE_PRIVATE );

			if ( sp != null
					&& sp.getBoolean( SysInfoManager.PREF_KEY_AUTO_START_ICON,
							false ) )
			{
				Util.updateIcons( context, sp );
			}
		}
	}
}