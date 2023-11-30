#!/usr/bin/env python3
"""
filename: worker.py
Author: Dave Watt <dave.watt@autodesk.com>
Description: This script is intended to provide the skeleton for a CloudOS compute worker - registering, retrieving a job, and reporting progress.
"""

import asyncio
import datetime
import logging
import subprocess  # nosec
import sys
import time

from compute.worker import Worker, AsyncState, FinalState, JobParameters


async def do_work(job_parameters: JobParameters, async_state: AsyncState) -> None:
    start_time = datetime.datetime.now()
    progress_duration = job_parameters.payload['testDefinition']['duration']
    progress_interval = job_parameters.payload['testDefinition']['progressInterval']
    progress_until = job_parameters.payload['testDefinition']['progressUntil']
    progress_until = progress_until if (0 <= progress_until <= 100) else 100

    while not async_state.canceled_or_completed:
        await asyncio.sleep(progress_interval)
        current_progress = (datetime.datetime.now() - start_time).total_seconds() * 100.0 / progress_duration if progress_duration > 0 else 100
        if current_progress > progress_until:
            current_progress = progress_until

        current_progress = int(current_progress)
        logging.info('Progress: {}'.format(current_progress))
        async_state.current_progress = current_progress
        async_state.progress_details = {'stage': '{} percent'.format(current_progress)}

        if current_progress >= progress_until:
            async_state.canceled_or_completed = True
            async_state.final_state = job_parameters.payload['testDefinition']['output']


def stress_test_start(job_parameters):
    cpu = job_parameters.payload['testDefinition'].get('cpu')
    if cpu is None:
        return None

    memory = job_parameters.payload['testDefinition'].get('memory')
    hdd = job_parameters.payload['testDefinition'].get('hdd')

    duration = job_parameters.payload['testDefinition']['duration']
    progress_until = job_parameters.payload['testDefinition']['progressUntil']
    if duration == 0:
        duration = 1
    duration = duration * progress_until / 100.0

    # invoke 'stress-ng -c 1 --vm 1 --vm-bytes 100M -d 1 --hdd-bytes 2M -t 10'
    # invoke 'stress    -c 1   -m 1 --vm-bytes 100M -d 1 --hdd-bytes 2M -t 10'

    args = ["stress", "-c", str(cpu), "-t", str(duration)]

    if memory is not None:
        args.extend(["-m", str(1), "--vm-bytes", str(memory) + "M"])

    if hdd is not None:
        args.extend(["-d", str(1), "--hdd-bytes", str(hdd) + "M"])

    proc = subprocess.Popen(args)
    return proc


def stress_test_end(proc):
    if proc is not None:
        proc.terminate()
        proc.wait()


async def main() -> None:
    worker = Worker()

    # Try to call something out on the Internet to make sure we've got the ability to make external connections
    # response = requests.get('https://www.autodesk.com', timeout=10)
    # response.raise_for_status()

    while True:
        current_seconds = int(round(time.time()))
        job_parameters = None
        try:
            job_parameters = await worker.load_next_job()
        except:
            e = sys.exc_info()[1]
            # don't log 404's (Job not found), those are expected when there's no job
            if not "Job not found" in str(e):
                logging.exception("Unknown exception calling load_next_job")
                raise

        if job_parameters:
            st = stress_test_start(job_parameters)
            should_do_heartbeat = (job_parameters.payload['testDefinition']['heartbeatUntil'] > 0)
            should_do_progress = (job_parameters.payload['testDefinition']['progressUntil'] > 0)
            should_do_work = (job_parameters.payload['testDefinition']['duration'] > 0)

            tasks = []
            if should_do_heartbeat:
                tasks.append(asyncio.create_task(worker.do_heartbeats(
                    job_parameters=job_parameters,
                    heartbeat_interval_seconds=job_parameters.payload['testDefinition']['heartbeatInterval'],
                    async_state=worker.async_state,
                    heartbeat_stop_after_seconds=job_parameters.payload['testDefinition']['heartbeatUntil']
                )))
            if should_do_progress:
                tasks.append(asyncio.create_task(worker.do_progress(
                    job_parameters=job_parameters,
                    progress_interval_seconds=job_parameters.payload['testDefinition']['progressInterval'],
                    async_state=worker.async_state
                )))

            if should_do_work:
                tasks.append(
                    asyncio.create_task(do_work(job_parameters=job_parameters, async_state=worker.async_state)))

            if len(tasks):
                await asyncio.wait(tasks)
            else:
                worker.async_state.current_progress = 100

            final_state = FinalState.COMPLETED if worker.async_state.current_progress >= 100 else FinalState.FAILED
            await worker.complete(job_parameters, final_state, worker.async_state)
            stress_test_end(st)
        elif worker.WORKER_IS_POLLING:
            # None returned. That is expected when we don't have a job available for a polling worker.
            # However, if we get a failure or otherwise don't get a job, we need to ensure we're not in a
            # hard loop, so here we add a backoff.
            elapsed_seconds = int(round(time.time())) - current_seconds
            remainder = elapsed_seconds - worker.POLL_TIMEOUT
            if remainder > 0:
                logging.warning(
                    f"load_next_job returned without job before poll timeout, sleeping for {remainder} seconds")
                asyncio.sleep(remainder)

        if not worker.WORKER_IS_POLLING:
            return


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    # Execute only when run as a script
    EVENT_LOOP = asyncio.get_event_loop()
    try:
        EVENT_LOOP.run_until_complete(main())
    finally:
        EVENT_LOOP.close()
