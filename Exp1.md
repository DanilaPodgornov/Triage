## Эталонные значения

| Key       | Execution count | Failed |
| --------- | --------------- | ------ |
| SQM-T691  | 78              | 22     |
| SQM-T1470 | 40              | 19     |
| SQM-T462  | 45              | 18     |
| SQM-T449  | 81              | 17     |
| SQM-T235  | 81              | 12     |

## Расчет failureWeight для каждого теста

failureWeight = failuresCount + failedExecutionsRatio + lastExecutionStatus

| Key       | failuresCount | failedExecutionsRatio | lastExecutionStatus | failureWeight |
| --------- | ------------- | --------------------- | ------------------- | ------------- |
| SQM-T691  | 0.0538        | 0.0538                | 0.1769              | 0.2845        |
| SQM-T1470 |               |                       |                     |               |
| SQM-T462  |               |                       |                     |               |
| SQM-T449  | 0.1136        |                       |                     |               |
| SQM-T235  |               |                       |                     |               |