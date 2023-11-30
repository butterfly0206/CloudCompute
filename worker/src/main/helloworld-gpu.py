#!/usr/bin/env python3
"""
filename: helloworker-gpu.py
Author: Dave Watt <dave.watt@autodesk.com>
Description: This script is intended to provide the skeleton for a CloudOS compute GPU worker - registering, retrieving a job, and reporting progress.
"""

import asyncio
import logging
import subprocess   # nosec

from compute.worker import Worker, AsyncState, FinalState


async def do_work(job_payload: dict, work_interval: int, progress_step: int, async_state: AsyncState) -> None:
    async_state.current_progress = 100
    # run() returns a CompletedProcess object if it was successful
    # errors in the created process are raised here too
    try:
        process = subprocess.run(["/bin/sh", "-c", "nvidia-smi"], check=True, stdout=subprocess.PIPE, universal_newlines=True)
        async_state.final_result = {
            'stdout': process.stdout,
            'stderr': process.stderr
        }
    except subprocess.CalledProcessError:
        async_state.current_progress = 0
    async_state.canceled_or_completed = True


async def main() -> None:
    worker = Worker()

    while True:
        job_parameters = await worker.load_next_job()

        if job_parameters:
            await asyncio.gather(
                worker.do_heartbeats(job_parameters, heartbeat_interval_seconds=5, async_state=worker.async_state),
                do_work(job_parameters.payload, work_interval=1, progress_step=3, async_state=worker.async_state)
            )
            final_state = FinalState.COMPLETED if worker.async_state.current_progress == 100 else FinalState.FAILED
            await worker.complete(job_parameters, final_state, worker.async_state)

        if not worker.WORKER_IS_POLLING:
            return


if __name__ ==  "__main__":
    logging.basicConfig(level=logging.DEBUG)
    # Execute only when run as a script
    EVENT_LOOP = asyncio.get_event_loop()
    try:
        EVENT_LOOP.run_until_complete(main())
    finally:
        EVENT_LOOP.close()
