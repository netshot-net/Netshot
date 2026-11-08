# Tasks

The Tasks section shows Netshot's current and scheduled work, and lets you schedule global tasks.

Use the tabs to filter by state: **Running**, **Scheduled** (waiting), **Succeeded**, **Failed**, **Cancelled**. Past-task tabs default to showing today's tasks; pick another day to see history. Click the refresh button to update the list.

Schedule a global task via **Schedule...** in the main toolbar. To see the task history of a single device, open its **Tasks** tab from the [Devices](devices.md) view.

## Task hierarchy

A task run over a group of devices (a snapshot, a script run, a diagnostic, a compliance check...) is a **parent task** that spawns one **child task per device**, run in parallel. The task list shows this hierarchy, so you can drill from the group-level result down to what happened on each individual device. A parent task's own status reflects whether it managed to schedule its children, not the per-device outcomes — a failure on one device doesn't by itself fail the parent.

## Tasks on the same device

Only one task works on a given device at a time (snapshot, diagnostics and script tasks; compliance checks only use the stored configuration and are not concerned). When such a task starts while another one is already working on the same device:

- a **snapshot** or **diagnostics** task is **cancelled** if a task of the same type is already running or waiting on the device (the other one will capture the current state anyway); a recurring task is still rescheduled for its next occurrence;
- otherwise, the task **waits** for the device to be released (it shows as running, and its log says which task it is waiting for). **Script** tasks are never cancelled because of another task: they always wait for their turn.

A task waiting for a device can be cancelled from its task dialog (**Cancel task**). This only works while it is still waiting: a task already working on the device can't be stopped. A recurring task cancelled this way is not rescheduled.

## Script chaining

When running a script against a device (or a group), you can ask Netshot to automatically:

- take a **snapshot** of the device once the script finishes, and/or
- run **diagnostics** followed by a **compliance check** right after.

This saves triggering those as separate, manually-timed tasks after a change.

## Script folders

Saved scripts can be organized into folders in the script library, making them easier to find and manage as the collection grows — see [Running scripts on devices](devices.md#running-scripts-on-devices).
