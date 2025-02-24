import { TransportOptions } from 'mediasoup-client/lib/Transport';
import { ACTION, EVENT } from './constants';
import { ConnectTransportRequest, ConsumerData, ConsumeRequest, ConsumeResponse, ConsumerPreferredLayers, NumWorkersData, PipeFromRemoteProducerRequest, PipeToRemoteProducerRequest, PipeTransportConnectData, PipeTransportData, ProducerData, ProduceRequest, ProduceResponse, ServerConfigs, RecordingData, StatsInput, StatsOutput, StreamFileRequest, TransportBitrateData, TransportData, WorkerLoadData, ListData, StreamData, FilePathInput, PullStreamInputsRequest, PushStreamInputsRequest, PullStreamInputsResponse, PushStreamInputsResponse, RecordingRequest, StreamKindsData, KindsByFileInput, KindsOptionsData, PushStreamOptionsResponse, PushStreamOptionsRequest, PushStreamRequest, LiveStreamRequest, StreamKindData, StreamListenData, MixerInput, MixerAddAudioData, MixerAddVideoData, MixerUpdateData, MixerRemoveData, MixerPipeLiveData, MixerPipeRecordingData, MixerPipeRtmpData, MixerPipeInput, MixerPipeStopInput, MixerCreateOptions, MixerPipeHlsData, LiveToHlsRequest } from './client-interfaces';
export interface IMediasoupApiClient {
    on(event: 'error', listener: (error: any) => void): this;
    on(event: 'connect', listener: (data: any) => void): this;
    on(event: 'disconnect', listener: (data: any) => void): this;
    on(event: EVENT.STREAM_STARTED, listener: (json: StreamKindData) => void): this;
    on(event: EVENT.STREAM_STOPPED, listener: (json: StreamKindData) => void): this;
    on(event: EVENT.MIXER_STOPPED, listener: (json: MixerInput) => void): this;
}
export interface IMediasoupApi extends Record<ACTION, (json: {}) => Promise<{} | void>> {
    readonly client: IMediasoupApiClient;
    [ACTION.RESUME_CONSUMER](json: ConsumerData): Promise<void>;
    [ACTION.PAUSE_CONSUMER](json: ConsumerData): Promise<void>;
    [ACTION.SET_PREFERRED_LAYERS](json: ConsumerPreferredLayers): Promise<void>;
    [ACTION.RESUME_PRODUCER](json: ProducerData): Promise<void>;
    [ACTION.PAUSE_PRODUCER](json: ProducerData): Promise<void>;
    [ACTION.CLOSE_PRODUCER](json: ProducerData): Promise<void>;
    [ACTION.PRODUCE](json: ProduceRequest): Promise<ProduceResponse>;
    [ACTION.CONSUME](json: ConsumeRequest): Promise<ConsumeResponse>;
    [ACTION.CREATE_PIPE_TRANSPORT](): Promise<PipeTransportData>;
    [ACTION.CONNECT_PIPE_TRANSPORT](json: PipeTransportConnectData): Promise<void>;
    [ACTION.CLOSE_TRANSPORT](json: TransportData): Promise<void>;
    [ACTION.GET_SERVER_CONFIGS](): Promise<ServerConfigs>;
    [ACTION.CREATE_TRANSPORT](): Promise<TransportOptions>;
    [ACTION.CONNECT_TRANSPORT](json: ConnectTransportRequest): Promise<void>;
    [ACTION.FILE_STREAMING](json: StreamFileRequest): Promise<void>;
    [ACTION.LIVE_STREAMING](json: LiveStreamRequest): Promise<void>;
    [ACTION.LIVE_TO_HLS](json: LiveToHlsRequest): Promise<void>;
    [ACTION.STOP_FILE_STREAMING](json: StreamKindsData): Promise<void>;
    [ACTION.START_RECORDING](json: RecordingRequest): Promise<void>;
    [ACTION.STOP_RECORDING](json: RecordingData): Promise<void>;
    [ACTION.SET_MAX_INCOMING_BITRATE](json: TransportBitrateData): Promise<void>;
    [ACTION.TRANSPORT_STATS](json: StatsInput): Promise<StatsOutput>;
    [ACTION.CONSUMERS_STATS](json: StatsInput): Promise<StatsOutput>;
    [ACTION.PRODUCERS_STATS](json: StatsInput): Promise<StatsOutput>;
    [ACTION.PIPE_TO_REMOTE_PRODUCER](json: PipeToRemoteProducerRequest): Promise<void>;
    [ACTION.PIPE_FROM_REMOTE_PRODUCER](json: PipeFromRemoteProducerRequest): Promise<void>;
    [ACTION.WORKER_LOAD](): Promise<WorkerLoadData>;
    [ACTION.NUM_WORKERS](): Promise<NumWorkersData>;
    [ACTION.RECORDED_STREAMS](): Promise<ListData>;
    [ACTION.STREAM_RECORDINGS](json: StreamData): Promise<ListData>;
    [ACTION.DELETE_STREAM_RECORDINGS](json: StreamData): Promise<void>;
    [ACTION.DELETE_RECORDING](json: FilePathInput): Promise<void>;
    [ACTION.PUSH_TO_SERVER_INPUTS](json: PushStreamInputsRequest): Promise<PushStreamInputsResponse>;
    [ACTION.PUSH_TO_SERVER_OPTIONS](json: PushStreamOptionsRequest): Promise<PushStreamOptionsResponse>;
    [ACTION.PUSH_TO_SERVER](json: PushStreamRequest): Promise<void>;
    [ACTION.PULL_FROM_SERVER_INPUTS](json: PullStreamInputsRequest): Promise<PullStreamInputsResponse>;
    [ACTION.KINDS_BY_FILE](json: KindsByFileInput): Promise<KindsOptionsData>;
    [ACTION.REQUEST_KEYFRAME](json: ConsumerData): Promise<void>;
    [ACTION.LISTEN_STREAM_STARTED](json: StreamListenData): Promise<boolean>;
    [ACTION.LISTEN_STREAM_STOPPED](json: StreamKindData): Promise<boolean>;
    [ACTION.MIXER_START](json: MixerCreateOptions): Promise<MixerInput>;
    [ACTION.MIXER_CLOSE](json: MixerInput): Promise<void>;
    [ACTION.MIXER_ADD](json: MixerAddAudioData | MixerAddVideoData): Promise<void>;
    [ACTION.MIXER_UPDATE](json: MixerUpdateData): Promise<void>;
    [ACTION.MIXER_REMOVE](json: MixerRemoveData): Promise<void>;
    [ACTION.MIXER_PIPE_START](json: MixerPipeLiveData | MixerPipeRecordingData | MixerPipeRtmpData | MixerPipeHlsData): Promise<MixerPipeInput>;
    [ACTION.MIXER_PIPE_STOP](json: MixerPipeStopInput): Promise<void>;
    [ACTION.LISTEN_MIXER_STOPPED](json: MixerInput): Promise<boolean>;
}
