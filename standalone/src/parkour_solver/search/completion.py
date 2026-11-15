"""Catalogue completion accounting independent of legacy comparison policy."""


def generation_exhausted(report):
    return (report.get('termination')=='exhausted' and not report.get('queueDrops',0)
        and not report.get('remainingPrefixes',0)
        and all(generation_exhausted(parent) for parent in report.get('parents',[]))
        and not any(row.get('termination')=='choice_limit' for row in report.get('generation',[])))
