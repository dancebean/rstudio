/*
 * TerminalList.java
 *
 * Copyright (C) 2009-16 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.terminal;

import java.util.Iterator;
import java.util.LinkedHashMap;

import org.rstudio.core.client.StringUtil;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.console.ConsoleProcess.ConsoleProcessFactory;
import org.rstudio.studio.client.common.console.ConsoleProcessInfo;
import org.rstudio.studio.client.common.shell.ShellSecureInput;
import org.rstudio.studio.client.workbench.views.terminal.events.TerminalSubprocEvent;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * List of terminals, with sufficient metadata to display a list of
 * available terminals and reconnect to them.
 */
public class TerminalList implements Iterable<String>,
                                     TerminalSubprocEvent.Handler

{
   private static class TerminalMetadata
   {
      /**
       * Create a TerminalMetadata object
       * @param handle terminal handle, unique key
       * @param caption terminal caption, shown in terminal picker
       * @param title terminal title, shown in toolbar above active terminal
       * @param sequence terminal sequence number
       */
      private TerminalMetadata(String handle,
                               String caption,
                               String title,
                               int sequence,
                               boolean childProcs)
      {
         handle_ = StringUtil.notNull(handle);
         caption_ = StringUtil.notNull(caption);
         title_ = StringUtil.notNull(title);
         sequence_ = sequence;
         childProcs_ = childProcs;
      }

      private TerminalMetadata(TerminalMetadata original,
                               String newTitle)
      {
         this(original.handle_,
              original.caption_,
              newTitle,
              original.sequence_,
              original.childProcs_);
      }

      private TerminalMetadata(ConsoleProcessInfo procInfo)
      {
         this(procInfo.getHandle(),
              procInfo.getCaption(),
              procInfo.getTitle(),
              procInfo.getTerminalSequence(),
              procInfo.getHasChildProcs());
      }

      private TerminalMetadata(TerminalSession term)
      {
         this(term.getHandle(),
              term.getCaption(),
              term.getTitle(),
              term.getSequence(),
              term.getHasChildProcs());
      }

      /**
       * @return unique identifier for terminal
       */
      public String getHandle() { return handle_; }

      /**
       * @return caption for terminal, shown in terminal picker
       */
      public String getCaption() { return caption_; }

      /**
       * @return title for terminal, shown above the terminal pane
       */
      public String getTitle() { return title_; }

      /**
       * @return relative order of terminal creation, used to pick number for
       * unique default terminal caption, e.g. "Terminal 3"
       */
      public int getSequence() { return sequence_; }

      /**
       * @return true if terminal shell has child processes
       */
      public boolean getChildProcs() { return childProcs_; }

      private String handle_;
      private String caption_;
      private String title_;
      private int sequence_;
      private boolean childProcs_;
   }

   protected TerminalList() 
   {
      RStudioGinjector.INSTANCE.injectMembers(this); 
      eventBus_.addHandler(TerminalSubprocEvent.TYPE, this);
   }

   @Inject
   private void initialize(Provider<ConsoleProcessFactory> pConsoleProcessFactory,
                           EventBus events)
   {
      pConsoleProcessFactory_ = pConsoleProcessFactory;
      eventBus_ = events;
   }

   /**
    * Add metadata from supplied TerminalSession
    * @param terminal terminal to add
    */
   public void addTerminal(TerminalSession terminal)
   {
      addTerminal(new TerminalMetadata(terminal));
   }

   /**
    * Add metadata from supplied ConsoleProcessInfo
    * @param procInfo metadata to add
    */
   public void addTerminal(ConsoleProcessInfo procInfo)
   {
      addTerminal(new TerminalMetadata(procInfo));
   }

   /**
    * Change terminal title.
    * @param handle handle of terminal
    * @param title new title
    * @return true if title was changed, false if it was unchanged
    */
   public boolean retitleTerminal(String handle, String title)
   {
      TerminalMetadata current = getMetadataForHandle(handle);
      if (current == null)
      {
         return false;
      }

      if (!current.getTitle().equals(title))
      {
         addTerminal(new TerminalMetadata(current, title));
         return true;
      }
      return false;
   }

   /**
    * update has subprocesses flag
    * @param handle terminal handle
    * @param childProcs new subprocesses flag value
    * @return true if changed, false if unchanged
    */
   private boolean setChildProcs(String handle, boolean childProcs)
   {
      TerminalMetadata current = getMetadataForHandle(handle);
      if (current == null)
      {
         return false;
      }

      if (current.getChildProcs() != childProcs)
      {
         addTerminal(new TerminalMetadata(
               current.handle_,
               current.caption_,
               current.title_,
               current.sequence_,
               childProcs));
         return true;
      }
      return false;
   }

   /**
    * Remove given terminal from the list
    * @param handle terminal handle
    */
   void removeTerminal(String handle)
   {
      terminals_.remove(handle);
   }

   /**
    * Kill all known terminal server processes, remove them from the server-
    * side list, and from the client-side list.
    */
   void terminateAll()
   {
      for (final java.util.Map.Entry<String, TerminalMetadata> item : terminals_.entrySet())
      {
         pConsoleProcessFactory_.get().interruptAndReap(item.getValue().getHandle());
      }
      terminals_.clear();
   }

   /**
    * Number of terminals in cache.
    * @return number of terminals tracked by this object
    */
   public int terminalCount()
   {
      return terminals_.size();
   }

   /**
    * Return 0-based index of a terminal in the list.
    * @param handle terminal to find
    * @return 0-based index of terminal, -1 if not found
    */
   public int indexOfTerminal(String handle)
   {
      int i = 0;
      for (final java.util.Map.Entry<String, TerminalMetadata> item : terminals_.entrySet())
      {
         if (item.getValue().getHandle().equals(handle))
         {
            return i;
         }
         i++;
      }

      return -1;
   }

   /**
    * Return terminal handle at given 0-based index
    * @param i zero-based index
    * @return handle of terminal at index, or null if invalid index
    */
   public String terminalHandleAtIndex(int i)
   {
      int j = 0;
      for (final java.util.Map.Entry<String, TerminalMetadata> item : terminals_.entrySet())
      {
         if (i == j)
         {
            return item.getValue().getHandle();
         }
         j++;
      }
      return null;
   }

   /**
    * Get metadata for terminal with given handle.
    * @param handle handle of terminal of interest
    * @return terminal metadata or null if not found
    */
   private TerminalMetadata getMetadataForHandle(String handle)
   {
      return terminals_.get(handle);
   }

   /**
    * Initiate startup of a new terminal
    */
   public void createNewTerminal()
   {
      startTerminal(nextTerminalSequence(),
                    null,  // handle
                    null,  // caption
                    null,  // title
                    true); // childProcs
   }

   /**
    * Reconnect to a known terminal.
    * @param handle
    * @return true if terminal was known and reconnect initiated
    */
   public boolean reconnectTerminal(String handle)
   {
      TerminalMetadata existing = getMetadataForHandle(handle);
      if (existing == null)
      {
         return false;
      }

      startTerminal(existing.getSequence(),
                    handle,
                    existing.getCaption(),
                    existing.getTitle(),
                    existing.getChildProcs());
      return true;
   }

   /**
    * @param handle handle to find
    * @return caption for that handle
    */
   public String getCaption(String handle)
   {
      return getMetadataForHandle(handle).caption_;
   }

   /**
    * @return true if any of the terminal shells have subprocesses
    */
   public boolean haveSubprocs()
   {
      for (final TerminalMetadata item : terminals_.values())
      {
         if (item.childProcs_)
         {
            return true;
         }
      }
      return false;
   }

   /**
    * Choose a 1-based sequence number one higher than the highest currently 
    * known terminal number. We don't try to fill gaps if terminals are closed 
    * in the middle of the opened tabs.
    * @return Highest currently known terminal plus one
    */
   private int nextTerminalSequence()
   {
      int maxNum = ConsoleProcessInfo.SEQUENCE_NO_TERMINAL;
      for (final java.util.Map.Entry<String, TerminalMetadata> item : terminals_.entrySet())
      {
         maxNum = Math.max(maxNum, item.getValue().getSequence());
      }
      return maxNum + 1;
   }

   private ShellSecureInput getSecureInput()
   {
      if (secureInput_ == null)
      {
         secureInput_ = new ShellSecureInput();  
      }
      return secureInput_;
   }

   private void startTerminal(int sequence,
                             String terminalHandle,
                             String caption,
                             String title,
                             boolean hasChildProcs)
   {
      TerminalSession newSession = new TerminalSession(
            getSecureInput(), sequence, terminalHandle, caption, title,
            hasChildProcs);
      newSession.connect();
   }

   private void addTerminal(TerminalMetadata terminal)
   {
      terminals_.put(terminal.getHandle(), terminal);
   }

   @Override
   public Iterator<String> iterator()
   {
      return terminals_.keySet().iterator();
   }

   @Override
   public void onTerminalSubprocs(TerminalSubprocEvent event)
   {
      setChildProcs(event.getHandle(), event.hasSubprocs());
   }

   /**
    * Map of terminal handles to terminal metadata; order they are added
    * is the order they will be iterated.
    */
   private LinkedHashMap<String, TerminalMetadata> terminals_ = 
                new LinkedHashMap<String, TerminalMetadata>();
   private ShellSecureInput secureInput_;

   // Injected ----  
   private Provider<ConsoleProcessFactory> pConsoleProcessFactory_;
   private EventBus eventBus_;
}